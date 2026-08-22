package com.ggukmoney.beanzip.domain.tap.service;

import com.ggukmoney.beanzip.domain.tap.entity.UserTapProgress;
import com.ggukmoney.beanzip.domain.tap.repository.UserTapProgressRepository;
import com.ggukmoney.beanzip.domain.user.entity.AppUser;
import com.ggukmoney.beanzip.global.config.TapPolicyConfig;
import org.junit.jupiter.api.Test;

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
    void drawsTargetWithinConfiguredVariance() {
        TapPolicyConfig config = configWithGeneralCurve();

        for (int i = 0; i < 200; i++) {
            int target = userTapProgressService.drawNextTarget(1000, config);
            assertThat(target - 1000).isBetween(270, 330);
        }
    }

    /**
     * 운영 정책(base 20, variance 25%)을 그대로 넣어 "평균 20탭당 1P, 실제로는 15~25탭" 이
     * 유지되는지 못박는다. 감속 커브가 사라진 뒤로 이 간격은 하루 내내 균일하다.
     */
    @Test
    void keepsLivePolicyIntervalBetween15And25Taps() {
        TapPolicyConfig config = mock(TapPolicyConfig.class);
        when(config.curveGeneralBase()).thenReturn(20);
        when(config.curveGeneralVariance()).thenReturn(0.25);

        for (int i = 0; i < 500; i++) {
            int target = userTapProgressService.drawNextTarget(0, config);
            assertThat(target).isBetween(15, 25);
        }
    }

    private TapPolicyConfig configWithGeneralCurve() {
        TapPolicyConfig config = mock(TapPolicyConfig.class);
        when(config.curveGeneralBase()).thenReturn(300);
        when(config.curveGeneralVariance()).thenReturn(0.10);
        return config;
    }
}
