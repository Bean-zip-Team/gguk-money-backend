package com.ggukmoney.beanzip.domain.promotion.service;

import com.ggukmoney.beanzip.domain.user.entity.AppUser;
import com.ggukmoney.beanzip.global.config.PromotionPolicyConfig;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TapThousandCompletionTriggerTest {

    private final PromotionPolicyConfig policyConfig = mock(PromotionPolicyConfig.class);
    private final TapThousandCompletionTrigger trigger = new TapThousandCompletionTrigger(policyConfig);
    private final AppUser user = mock(AppUser.class);
    private final Instant now = Instant.parse("2026-09-06T00:00:00Z");

    @Test
    void usesItsOwnPromotionCodeAndAmount() {
        when(policyConfig.tapThousandAmount()).thenReturn(5L);

        assertThat(trigger.promotionCode()).isEqualTo(PromotionPolicyConfig.TAP_THOUSAND_PROMOTION_CODE);
        assertThat(trigger.amount()).isEqualTo(5L);
    }

    @Test
    void issuingSwitchIsSeparateFromKeycapMission() {
        when(policyConfig.tapThousandIssuingEnabled()).thenReturn(true);

        assertThat(trigger.issuingEnabled()).isTrue();
        // 키캡 스위치는 쳐다보지 않는다. 하나를 켰다고 다른 하나가 켜지면 안 된다.
        verify(policyConfig, never()).issuingEnabled();
    }

    @Test
    void grantsWhenNetTapCountReachesThreshold() {
        when(policyConfig.tapThousandThreshold()).thenReturn(1000);

        Optional<Integer> snapshot = trigger.evaluate(
                PromotionTriggerContext.tapThresholdCrossed(user, 1000L, now));

        assertThat(snapshot).contains(1000);
    }

    @Test
    void doesNotGrantBelowThreshold() {
        when(policyConfig.tapThousandThreshold()).thenReturn(1000);

        assertThat(trigger.evaluate(PromotionTriggerContext.tapThresholdCrossed(user, 999L, now)))
                .isEmpty();
    }

    @Test
    void doesNotQueryAnythingWhileEvaluating() {
        when(policyConfig.tapThousandThreshold()).thenReturn(1000);

        trigger.evaluate(PromotionTriggerContext.tapThresholdCrossed(user, 1500L, now));

        // 탭 배치는 상시로 들어온다. 판정에 조회가 붙으면 BEA-255 가 줄인 쿼리가 되돌아온다.
        verify(user, never()).getId();
    }
}
