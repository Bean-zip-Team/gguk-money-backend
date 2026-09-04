package com.ggukmoney.beanzip.domain.promotion.service;

import com.ggukmoney.beanzip.global.config.PromotionPolicyConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PromotionAudiencePolicyTest {

    private final PromotionPolicyConfig policyConfig = mock(PromotionPolicyConfig.class);
    private final PromotionAudiencePolicy policy = new PromotionAudiencePolicy(policyConfig);

    private final UUID userId = UUID.randomUUID();

    @Test
    @DisplayName("제외 목록에 없으면 대상이다")
    void notExcluded() {
        when(policyConfig.excludedUserIds()).thenReturn(Optional.of(Set.of(UUID.randomUUID())));

        assertThat(policy.isEligible(userId)).isTrue();
    }

    @Test
    @DisplayName("제외 목록에 있으면 대상이 아니다")
    void excluded() {
        when(policyConfig.excludedUserIds()).thenReturn(Optional.of(Set.of(userId)));

        assertThat(policy.isEligible(userId)).isFalse();
    }

    @Test
    @DisplayName("제외 목록을 읽지 못하면 지급하지 않는다 — 목록 없음으로 떨어져 전원 지급되면 안 된다")
    void unreadableListIsFailClosed() {
        when(policyConfig.excludedUserIds()).thenReturn(Optional.empty());

        assertThat(policy.isEligible(userId)).isFalse();
    }
}
