package com.ggukmoney.beanzip.domain.promotion.service;

import com.ggukmoney.beanzip.domain.promotion.entity.PromotionGrant;
import com.ggukmoney.beanzip.domain.promotion.event.PromotionGrantCreatedEvent;
import com.ggukmoney.beanzip.domain.promotion.repository.PromotionGrantRepository;
import com.ggukmoney.beanzip.global.config.PromotionPolicyConfig;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

/**
 * 키캡 도메인이 아는 유일한 협력자. 개봉 트랜잭션 안에서 호출된다.
 *
 * <p>여기서는 행을 하나 넣고 이벤트를 발행하는 것 말고 아무것도 하지 않는다. 외부 호출은
 * 커밋 이후로 미룬다 — 개봉 트랜잭션은 {@code keycap_box_account} 와 {@code user_keycap} 에
 * 행 락을 잡고 있어서, 여기서 mTLS 왕복을 하면 락이 그 시간만큼 물리고, 토스가 지급했는데
 * 뒤에서 롤백되면 지급 기록만 사라진다.
 *
 * <p><b>이 클래스는 예외를 던지지 않는다.</b> "던지고 삼키기"는 JPA 에서 통하지 않는다.
 * 제약 위반이 flush 되면 트랜잭션이 rollback-only 로 오염돼, 호출자가 삼켜도 커밋 시점에
 * {@code UnexpectedRollbackException} 이 난다. 그래서 중복은 선체크로 피하고 unique 제약은
 * 최후 방어선으로만 둔다. {@code REQUIRES_NEW} 도 쓰지 않는다 — 개봉이 롤백됐는데 지급 행만
 * 남으면 완성되지도 않은 키캡에 돈을 준다.
 */
@Service
@RequiredArgsConstructor
public class PromotionGrantIssuer {

    private static final Logger log = LoggerFactory.getLogger(PromotionGrantIssuer.class);

    private final PromotionGrantRepository promotionGrantRepository;
    private final PromotionAudiencePolicy audiencePolicy;
    private final PromotionPolicyConfig policyConfig;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 트리거를 주입이 아니라 파라미터로 받는다. 필드로 두면 구현체가 둘이 되는 순간
     * {@code NoUniqueBeanDefinitionException} 으로 컨텍스트가 뜨지 않고, 호출부가 어느 미션을
     * 발급하는지도 드러나지 않는다.
     */
    public void issueIfEligible(PromotionTrigger promotionTrigger, PromotionTriggerContext context) {
        UUID userId = context.user().getId();
        try {
            if (!promotionTrigger.issuingEnabled()) {
                return;
            }

            String promotionCode = promotionTrigger.promotionCode();
            if (promotionGrantRepository.existsByUserIdAndPromotionCode(userId, promotionCode)) {
                return;
            }
            if (!audiencePolicy.isEligible(userId)) {
                return;
            }

            Optional<Integer> snapshot = promotionTrigger.evaluate(context);
            if (snapshot.isEmpty()) {
                return;
            }

            PromotionGrant grant = promotionGrantRepository.save(PromotionGrant.createPending(
                    context.user(),
                    promotionCode,
                    promotionTrigger.amount(),
                    snapshot.get(),
                    context.occurredAt()
            ));

            // 반드시 활성 트랜잭션 안에서 발행해야 한다. 트랜잭션 밖이면 AFTER_COMMIT 리스너가
            // 등록되지 못해 이벤트가 예외도 없이 사라진다.
            eventPublisher.publishEvent(new PromotionGrantCreatedEvent(grant.getId()));
            log.info("Promotion grant created; userId={} promotionCode={} grantId={}",
                    userId, promotionCode, grant.getId());
        } catch (RuntimeException exception) {
            // 지급 실패가 개봉을 깨뜨리면 안 된다. 지급하지 않는 쪽으로 떨어진다.
            log.error("Failed to issue promotion grant; userId={}", userId, exception);
        }
    }
}
