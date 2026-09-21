package com.ggukmoney.beanzip.domain.promotion.service;

import com.ggukmoney.beanzip.domain.tap.entity.UserTapProgress;
import com.ggukmoney.beanzip.domain.tap.repository.UserTapProgressRepository;
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
    private final UserTapProgressRepository userTapProgressRepository = mock(UserTapProgressRepository.class);
    private final TapThousandCompletionTrigger trigger =
            new TapThousandCompletionTrigger(policyConfig, userTapProgressRepository);
    private final AppUser user = mock(AppUser.class);
    private final Instant now = Instant.parse("2026-09-06T00:00:00Z");
    private final java.util.UUID userId = java.util.UUID.randomUUID();

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
    void reportsProgressAsTapsSinceTheBaseline() {
        when(policyConfig.tapThousandThreshold()).thenReturn(1000);
        UserTapProgress progress = mock(UserTapProgress.class);
        when(progress.hasPromotionTapBaseline()).thenReturn(true);
        when(progress.getCumulativeValidTapCount()).thenReturn(4150L);
        when(progress.getPromotionTapBaseline()).thenReturn(3200L);
        when(userTapProgressRepository.findByUserId(userId)).thenReturn(Optional.of(progress));

        MissionProgress result = trigger.progressOf(userId);

        // 앱은 기준값을 모르므로 이 계산을 할 수 없다. 서버가 내려준다.
        assertThat(result.current()).isEqualTo(950L);
        assertThat(result.target()).isEqualTo(1000L);
        assertThat(result.reached()).isFalse();
    }

    @Test
    void reportsZeroProgressWhenBaselineIsNotAnchoredYet() {
        when(policyConfig.tapThousandThreshold()).thenReturn(1000);
        UserTapProgress progress = mock(UserTapProgress.class);
        when(progress.hasPromotionTapBaseline()).thenReturn(false);
        when(userTapProgressRepository.findByUserId(userId)).thenReturn(Optional.of(progress));

        assertThat(trigger.progressOf(userId).current()).isZero();
    }

    @Test
    void doesNotQueryAnythingWhileEvaluating() {
        when(policyConfig.tapThousandThreshold()).thenReturn(1000);

        trigger.evaluate(PromotionTriggerContext.tapThresholdCrossed(user, 1500L, now));

        // 탭 배치는 상시로 들어온다. 판정에 조회가 붙으면 BEA-255 가 줄인 쿼리가 되돌아온다.
        verify(user, never()).getId();
    }
}
