package com.ggukmoney.beanzip.domain.tap.service;

import com.ggukmoney.beanzip.global.config.TapPolicyConfig;
import com.ggukmoney.beanzip.domain.tap.entity.UserTapDaily;
import com.ggukmoney.beanzip.domain.tap.repository.UserTapDailyRepository;
import com.ggukmoney.beanzip.domain.user.entity.AppUser;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UserTapDailyServiceTest {

    private final UserTapDailyRepository userTapDailyRepository = mock(UserTapDailyRepository.class);
    private final UserTapDailyService userTapDailyService = new UserTapDailyService(userTapDailyRepository);

    @Test
    void returnsExistingRowWithoutCreatingWhenAlreadyPresent() {
        UUID userId = UUID.randomUUID();
        AppUser user = mock(AppUser.class);
        when(user.getId()).thenReturn(userId);
        UserTapDaily existing = mock(UserTapDaily.class);
        when(userTapDailyRepository.findByUserIdAndTapDate(eq(userId), any(LocalDate.class)))
                .thenReturn(Optional.of(existing));

        UserTapDaily result = userTapDailyService.getOrCreateToday(user, mock(TapPolicyConfig.class));

        assertThat(result).isEqualTo(existing);
    }

    @Test
    void createsNewRowWithFreshlyDrawnTargetWhenMissing() {
        UUID userId = UUID.randomUUID();
        AppUser user = mock(AppUser.class);
        when(user.getId()).thenReturn(userId);
        when(userTapDailyRepository.findByUserIdAndTapDate(eq(userId), any(LocalDate.class)))
                .thenReturn(Optional.empty());
        when(userTapDailyRepository.save(any(UserTapDaily.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserTapDaily result = userTapDailyService.getOrCreateToday(user, configWithGeneralCurve());

        assertThat(result.getNextPointTarget()).isBetween(270, 330);
    }

    @Test
    void drawsGeneralCurveTargetWithinConfiguredVarianceWhenBelowDecelThreshold() {
        TapPolicyConfig config = configWithGeneralCurve();

        for (int i = 0; i < 200; i++) {
            int target = userTapDailyService.drawNextTarget(1000, 6, config);
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
            int target = userTapDailyService.drawNextTarget(5000, 7, config);
            assertThat(target - 5000).isBetween(570, 630);
        }
    }

    private TapPolicyConfig configWithGeneralCurve() {
        TapPolicyConfig config = mock(TapPolicyConfig.class);
        when(config.decelThresholdPoints()).thenReturn(7);
        when(config.curveGeneralBase()).thenReturn(300);
        when(config.curveGeneralVariance()).thenReturn(0.10);
        return config;
    }
}
