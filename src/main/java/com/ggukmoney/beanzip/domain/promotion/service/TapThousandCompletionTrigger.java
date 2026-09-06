package com.ggukmoney.beanzip.domain.promotion.service;

import com.ggukmoney.beanzip.global.config.PromotionPolicyConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * 커트오프 이후 순증 탭이 임계치 이상이면 자격을 준다 (BEA-278).
 *
 * <p><b>추가 조회를 하지 않는다.</b> 키캡 트리거는 커트오프를 적용해 다시 세지만, 탭은 배치가
 * 상시로 들어와서 그렇게 하면 판정마다 쿼리가 붙는다(BEA-255 가 줄인 것을 되돌린다). 대신
 * 호출부가 이미 들고 있는 누적값과 기준값의 차이를 넘겨주고, 여기서는 비교만 한다.
 *
 * <p>소급 방지는 기준값 스냅샷이 책임진다. 탭에는 시각이 없고 누적 카운터뿐이라
 * {@code completed_at} 같은 필터를 걸 수 없다 — 커트오프 시점의 누적값을 유저별로 박아두고
 * 그 차이만 센다. 커트오프가 설정되지 않았으면 지급하지 않는다(fail-closed).
 */
@Component
@RequiredArgsConstructor
public class TapThousandCompletionTrigger implements PromotionTrigger {

    private final PromotionPolicyConfig policyConfig;

    @Override
    public String promotionCode() {
        return PromotionPolicyConfig.TAP_THOUSAND_PROMOTION_CODE;
    }

    @Override
    public long amount() {
        return policyConfig.tapThousandAmount();
    }

    @Override
    public boolean issuingEnabled() {
        return policyConfig.tapThousandIssuingEnabled();
    }

    @Override
    public Optional<Integer> evaluate(PromotionTriggerContext context) {
        long netTapCount = context.triggerValue();
        if (netTapCount < policyConfig.tapThousandThreshold()) {
            return Optional.empty();
        }
        return Optional.of(Math.toIntExact(Math.min(netTapCount, Integer.MAX_VALUE)));
    }
}
