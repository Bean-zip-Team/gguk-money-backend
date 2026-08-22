package com.ggukmoney.beanzip.domain.tap.service;

import com.ggukmoney.beanzip.domain.tap.entity.UserTapDaily;
import com.ggukmoney.beanzip.domain.tap.entity.UserTapProgress;
import com.ggukmoney.beanzip.domain.tap.repository.UserTapProgressRepository;
import com.ggukmoney.beanzip.domain.user.entity.AppUser;
import com.ggukmoney.beanzip.global.config.TapPolicyConfig;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UserTapProgressServiceTest {

    private final UserTapProgressRepository userTapProgressRepository = mock(UserTapProgressRepository.class);
    private final UserTapProgressService userTapProgressService = new UserTapProgressService(userTapProgressRepository);

    @Test
    void createsRowWithFreshlyDrawnPointTarget() {
        AppUser user = mock(AppUser.class);
        when(userTapProgressRepository.save(any(UserTapProgress.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserTapProgress result = userTapProgressService.createFor(user, configWithGeneralCurve());

        assertThat(result.getNextPointTarget()).isBetween(270, 330);
        assertThat(result.getCumulativeValidTapCount()).isZero();
    }

    @Test
    void returnsExistingRowForUser() {
        UUID userId = UUID.randomUUID();
        UserTapProgress existing = mock(UserTapProgress.class);
        when(userTapProgressRepository.findByUserId(userId)).thenReturn(Optional.of(existing));

        UserTapProgress result = userTapProgressService.getForUser(userId);

        assertThat(result).isEqualTo(existing);
    }

    @Test
    void drawsGeneralCurveTargetWithinConfiguredVarianceWhenBelowDecelThreshold() {
        TapPolicyConfig config = configWithGeneralCurve();

        for (int i = 0; i < 200; i++) {
            int target = userTapProgressService.drawNextTarget(1000, 6, config);
            assertThat(target - 1000).isBetween(270, 330);
        }
    }

    @Test
    void drawsDecelCurveTargetWithinConfiguredVarianceWhenAtOrAboveDecelThreshold() {
        TapPolicyConfig config = mock(TapPolicyConfig.class);
        when(config.decelThresholdPoints()).thenReturn(7);
        when(config.curveDecelBase()).thenReturn(600);
        when(config.curveDecelVariance()).thenReturn(0.05);

        for (int i = 0; i < 200; i++) {
            int target = userTapProgressService.drawNextTarget(5000, 7, config);
            assertThat(target - 5000).isBetween(570, 630);
        }
    }

    @Test
    void returnsGapToNextTargetBeforeAnyCapIsReached() {
        AppUser user = mock(AppUser.class);
        UserTapDaily daily = UserTapDaily.createFor(user, LocalDate.of(2026, 7, 21));
        daily.addValidTaps(120);
        UserTapProgress progress = UserTapProgress.createFor(user, 300);
        progress.addValidTaps(120);

        int remaining = userTapProgressService.remainingTapsToNextPoint(progress, daily, configWithCaps());

        assertThat(remaining).isEqualTo(180);
    }

    @Test
    void returnsZeroWhenDailyPointCapIsReached() {
        AppUser user = mock(AppUser.class);
        UserTapDaily daily = UserTapDaily.createFor(user, LocalDate.of(2026, 7, 21));
        daily.addValidTaps(100);
        for (int i = 0; i < 150; i++) {
            daily.incrementPointEarned();
        }
        UserTapProgress progress = UserTapProgress.createFor(user, 300);
        progress.addValidTaps(100);

        int remaining = userTapProgressService.remainingTapsToNextPoint(progress, daily, configWithCaps());

        assertThat(remaining).isZero();
    }

    @Test
    void returnsZeroWhenDailyTapCapIsReachedEvenThoughTargetRemains() {
        AppUser user = mock(AppUser.class);
        UserTapDaily daily = UserTapDaily.createFor(user, LocalDate.of(2026, 7, 21));
        daily.addValidTaps(3000);
        UserTapProgress progress = UserTapProgress.createFor(user, 3010);
        progress.addValidTaps(3000);

        // 진행도가 멈춘 상태라 10탭 남은 것처럼 보이면 안 된다.
        int remaining = userTapProgressService.remainingTapsToNextPoint(progress, daily, configWithCaps());

        assertThat(remaining).isZero();
    }

    private TapPolicyConfig configWithCaps() {
        TapPolicyConfig config = mock(TapPolicyConfig.class);
        when(config.pointDailyCap()).thenReturn(150);
        when(config.maxPerDay()).thenReturn(3000);
        return config;
    }

    private TapPolicyConfig configWithGeneralCurve() {
        TapPolicyConfig config = mock(TapPolicyConfig.class);
        when(config.decelThresholdPoints()).thenReturn(7);
        when(config.curveGeneralBase()).thenReturn(300);
        when(config.curveGeneralVariance()).thenReturn(0.10);
        return config;
    }
}
