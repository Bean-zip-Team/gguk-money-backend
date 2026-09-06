package com.ggukmoney.beanzip.domain.promotion.service;

import com.ggukmoney.beanzip.domain.keycap.entity.UserKeycap;
import com.ggukmoney.beanzip.domain.keycap.repository.UserKeycapRepository;
import com.ggukmoney.beanzip.global.config.PromotionPolicyConfig;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;

/**
 * 완성 키캡이 임계치 이상이면 자격을 준다.
 *
 * <p>{@code >= threshold} 이고 {@code == threshold} 가 아니다. 정확히 임계치일 때만 주면 그
 * 순간을 한 번 놓친 유저에게 복구 경로가 없다 — auto-flush 가 깨지거나, 제외 목록 조회가 순간
 * 실패하거나, 킬스위치를 켜기 전에 완성했거나 하면 영원히 못 받는다. {@code >=} 는 다음
 * 개봉에서 자연히 복구되고, 중복은 발급 단계의 선체크와 unique 제약이 막는다.
 *
 * <p>대신 "소급 없음"은 커트오프가 책임진다. 커트오프 이후에 완성된 키캡만 세므로, 출시 시점에
 * 이미 임계치를 넘긴 유저는 대상이 아니다. <b>커트오프는 선택 구현이 아니라 {@code >=} 와 한
 * 몸이다.</b> 빠지면 기존 보유자 전원에게 지급되고 그 돈은 회수되지 않는다.
 */
@Component
@RequiredArgsConstructor
public class KeycapFiveCompletionTrigger implements PromotionTrigger {

    private static final Logger log = LoggerFactory.getLogger(KeycapFiveCompletionTrigger.class);

    private final UserKeycapRepository userKeycapRepository;
    private final PromotionPolicyConfig policyConfig;

    @Override
    public String promotionCode() {
        return PromotionPolicyConfig.KEYCAP_FIVE_PROMOTION_CODE;
    }

    @Override
    public long amount() {
        return policyConfig.amount();
    }

    @Override
    public Optional<Integer> evaluate(PromotionTriggerContext context) {
        Optional<Instant> launchAt = policyConfig.launchAt();
        if (launchAt.isEmpty()) {
            // 커트오프 없이 지급하면 기존 보유자 전원에게 나간다. 값이 정해질 때까지 멈춘다.
            log.warn("Promotion launch cutoff is not configured; skipping grant for user={}",
                    context.user().getId());
            return Optional.empty();
        }

        int threshold = policyConfig.threshold();
        long eligible = userKeycapRepository.countByUserIdAndStatusAndCompletedAtAfter(
                context.user().getId(), UserKeycap.Status.COMPLETED, launchAt.get());

        if (eligible < threshold) {
            return Optional.empty();
        }
        return Optional.of(Math.toIntExact(Math.min(eligible, Integer.MAX_VALUE)));
    }
}
