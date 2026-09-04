package com.ggukmoney.beanzip.global.config;

import com.ggukmoney.beanzip.global.config.entity.AppConfig;
import com.ggukmoney.beanzip.global.config.repository.AppConfigRepository;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OnboardingRewardConfigTest {

    private final AppConfigRepository appConfigRepository = mock(AppConfigRepository.class);
    private final OnboardingRewardConfig config = new OnboardingRewardConfig(appConfigRepository);

    @Test
    void resolvesRewardPolicyFromAppConfigRows() {
        when(appConfigRepository.findFirstByConfigKeyAndEffectiveAtLessThanEqualOrderByEffectiveAtDesc(
                OnboardingRewardConfig.KEY_REWARD_KEYCAP_CODE,
                Instant.now()
        )).thenReturn(Optional.empty());
        when(appConfigRepository.findFirstByConfigKeyAndEffectiveAtLessThanEqualOrderByEffectiveAtDesc(
                org.mockito.ArgumentMatchers.eq(OnboardingRewardConfig.KEY_REWARD_KEYCAP_CODE),
                any(Instant.class)
        )).thenReturn(Optional.of(AppConfig.createFor(OnboardingRewardConfig.KEY_REWARD_KEYCAP_CODE, "\"main\"", Instant.EPOCH)));
        when(appConfigRepository.findFirstByConfigKeyAndEffectiveAtLessThanEqualOrderByEffectiveAtDesc(
                org.mockito.ArgumentMatchers.eq(OnboardingRewardConfig.KEY_BONUS_KEYCAP_GRADE),
                any(Instant.class)
        )).thenReturn(Optional.of(AppConfig.createFor(OnboardingRewardConfig.KEY_BONUS_KEYCAP_GRADE, "\"COMMON\"", Instant.EPOCH)));
        when(appConfigRepository.findFirstByConfigKeyAndEffectiveAtLessThanEqualOrderByEffectiveAtDesc(
                org.mockito.ArgumentMatchers.eq(OnboardingRewardConfig.KEY_REWARD_POINT_AMOUNT),
                any(Instant.class)
        )).thenReturn(Optional.of(AppConfig.createFor(OnboardingRewardConfig.KEY_REWARD_POINT_AMOUNT, "2", Instant.EPOCH)));
        when(appConfigRepository.findFirstByConfigKeyAndEffectiveAtLessThanEqualOrderByEffectiveAtDesc(
                org.mockito.ArgumentMatchers.eq(OnboardingRewardConfig.KEY_ATTEMPT_TTL_SECONDS),
                any(Instant.class)
        )).thenReturn(Optional.of(AppConfig.createFor(OnboardingRewardConfig.KEY_ATTEMPT_TTL_SECONDS, "900", Instant.EPOCH)));

        OnboardingRewardConfig.OnboardingRewardPolicy policy = config.resolve();

        assertThat(policy.rewardKeycapCode()).isEqualTo("main");
        assertThat(policy.bonusKeycapGrade()).isEqualTo("COMMON");
        assertThat(policy.rewardPointAmount()).isEqualTo(2);
        assertThat(policy.attemptTtl()).isEqualTo(Duration.ofMinutes(15));
    }

    @Test
    void rejectsMissingOrInvalidConfig() {
        assertThatThrownBy(config::resolve)
                .isInstanceOf(ResponseStatusException.class)
                .extracting(exception -> ((ResponseStatusException) exception).getReason())
                .isEqualTo("ONBOARDING_REWARD_NOT_AVAILABLE");
    }
}
