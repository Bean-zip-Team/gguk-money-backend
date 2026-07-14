package com.ggukmoney.beanzip.domain.tap.service;

import com.ggukmoney.beanzip.domain.tap.dto.BoxProgressSnapshot;
import com.ggukmoney.beanzip.domain.tap.entity.UserTapProgress;
import com.ggukmoney.beanzip.domain.tap.repository.UserTapProgressRepository;
import com.ggukmoney.beanzip.domain.user.entity.AppUser;
import com.ggukmoney.beanzip.global.config.TapPolicyConfig;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserTapProgressServiceTest {

    private final UserTapProgressRepository userTapProgressRepository = mock(UserTapProgressRepository.class);
    private final UserTapProgressService userTapProgressService = new UserTapProgressService(userTapProgressRepository);

    @Test
    void createsRowWithFreshlyDrawnPointAndBoxTargets() {
        AppUser user = mock(AppUser.class);
        when(userTapProgressRepository.save(any(UserTapProgress.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserTapProgress result = userTapProgressService.createFor(user, configWithGeneralCurveAndBoxDrop());

        assertThat(result.getNextPointTarget()).isBetween(270, 330);
        assertThat(result.getNextBoxTarget()).isBetween(180, 220);
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
    void returnsReadOnlyBoxProgressSnapshot() {
        UUID userId = UUID.randomUUID();
        AppUser user = mock(AppUser.class);
        UserTapProgress progress = UserTapProgress.createFor(user, 300, 100);
        progress.addValidTaps(45);
        when(userTapProgressRepository.findByUserId(userId)).thenReturn(Optional.of(progress));

        BoxProgressSnapshot snapshot = userTapProgressService.getBoxProgress(userId);

        assertThat(snapshot.cumulativeValidTapCount()).isEqualTo(45);
        assertThat(snapshot.nextBoxTarget()).isEqualTo(100);
        assertThat(progress.getCumulativeValidTapCount()).isEqualTo(45);
        assertThat(progress.getNextBoxTarget()).isEqualTo(100);
        verify(userTapProgressRepository, never()).save(any(UserTapProgress.class));
    }

    @Test
    void rejectsMissingBoxProgressWithExistingPolicy() {
        UUID userId = UUID.randomUUID();
        when(userTapProgressRepository.findByUserId(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userTapProgressService.getBoxProgress(userId))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(exception -> ((ResponseStatusException) exception).getReason())
                .isEqualTo("TAP_PROGRESS_NOT_FOUND");

        verify(userTapProgressRepository, never()).save(any(UserTapProgress.class));
    }

    @Test
    void drawsGeneralCurveTargetWithinConfiguredVarianceWhenBelowDecelThreshold() {
        TapPolicyConfig config = configWithGeneralCurveAndBoxDrop();

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
    void drawsBoxTargetWithinConfiguredVarianceAroundBase() {
        TapPolicyConfig config = configWithGeneralCurveAndBoxDrop();

        for (int i = 0; i < 200; i++) {
            int target = userTapProgressService.drawNextBoxTarget(3000, config);
            assertThat(target - 3000).isBetween(180, 220);
        }
    }

    @Test
    void drawsFixedBoxTargetWhenVarianceIsZero() {
        TapPolicyConfig config = mock(TapPolicyConfig.class);
        when(config.boxDropBase()).thenReturn(200);
        when(config.boxDropVariance()).thenReturn(0.0);

        for (int i = 0; i < 50; i++) {
            int target = userTapProgressService.drawNextBoxTarget(3000, config);
            assertThat(target).isEqualTo(3200);
        }
    }

    private TapPolicyConfig configWithGeneralCurveAndBoxDrop() {
        TapPolicyConfig config = mock(TapPolicyConfig.class);
        when(config.decelThresholdPoints()).thenReturn(7);
        when(config.curveGeneralBase()).thenReturn(300);
        when(config.curveGeneralVariance()).thenReturn(0.10);
        when(config.boxDropBase()).thenReturn(200);
        when(config.boxDropVariance()).thenReturn(0.10);
        return config;
    }
}
