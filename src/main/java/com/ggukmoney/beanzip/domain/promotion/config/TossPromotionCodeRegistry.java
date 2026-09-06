package com.ggukmoney.beanzip.domain.promotion.config;

import com.ggukmoney.beanzip.global.config.PromotionPolicyConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 우리 내부 프로모션 코드 → 앱인토스 프로모션 코드.
 *
 * <p>실행기가 토스 코드를 단일 {@code @Value} 로 들고 있으면 프로모션이 둘이 되는 순간
 * 두 번째 미션의 지급이 첫 번째 프로모션 예산에서 빠져나간다. 토스는 금액이 그 프로모션의
 * 1회 한도 안이면 정상 지급으로 처리하므로 에러도 나지 않는다 — 예산이 마르고 나서야 안다.
 * 그래서 코드 결정을 grant 기준으로 옮긴다.
 *
 * <p>등록되지 않았거나 값이 비어 있으면 {@link Optional#empty()} 다. 호출자는 지급하지 않고
 * 설정 오류로 처리해야 한다(fail-closed).
 */
@Component
public class TossPromotionCodeRegistry {

    private final Map<String, String> tossCodeByPromotionCode;

    public TossPromotionCodeRegistry(
            @Value("${app.promotion.toss.keycap-five-code:}") String keycapFiveCode,
            @Value("${app.promotion.toss.tap-thousand-code:}") String tapThousandCode
    ) {
        Map<String, String> codes = new LinkedHashMap<>();
        putIfPresent(codes, PromotionPolicyConfig.KEYCAP_FIVE_PROMOTION_CODE, keycapFiveCode);
        putIfPresent(codes, PromotionPolicyConfig.TAP_THOUSAND_PROMOTION_CODE, tapThousandCode);
        this.tossCodeByPromotionCode = Map.copyOf(codes);
    }

    public Optional<String> tossCodeOf(String promotionCode) {
        if (promotionCode == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(tossCodeByPromotionCode.get(promotionCode));
    }

    private static void putIfPresent(Map<String, String> codes, String promotionCode, String tossCode) {
        if (StringUtils.hasText(tossCode)) {
            codes.put(promotionCode, tossCode.trim());
        }
    }
}
