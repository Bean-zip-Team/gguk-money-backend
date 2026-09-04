package com.ggukmoney.beanzip.domain.promotion.service;

import com.ggukmoney.beanzip.global.config.PromotionPolicyConfig;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** 지급 대상 여부. 판단할 수 없으면 대상이 아니라고 답한다(fail-closed). */
@Component
@RequiredArgsConstructor
public class PromotionAudiencePolicy {

    private static final Logger log = LoggerFactory.getLogger(PromotionAudiencePolicy.class);

    private final PromotionPolicyConfig policyConfig;

    public boolean isEligible(UUID userId) {
        Optional<Set<UUID>> excluded = policyConfig.excludedUserIds();
        if (excluded.isEmpty()) {
            // 제외 목록을 못 읽었다. "목록 없음"으로 떨어져 전원 지급되면 안 된다.
            log.error("Excluded user list unavailable; skipping grant for user={}", userId);
            return false;
        }
        return !excluded.get().contains(userId);
    }
}
