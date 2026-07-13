package com.ggukmoney.beanzip.global.config;

import com.ggukmoney.beanzip.global.config.repository.AppConfigRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TapPolicyConfigTest {

    private final AppConfigRepository appConfigRepository = mock(AppConfigRepository.class);
    private final TapPolicyConfig tapPolicyConfig = new TapPolicyConfig(appConfigRepository);

    @Test
    void usesDefaultsWhenRowsAreMissing() {
        when(appConfigRepository.findFirstByConfigKeyAndEffectiveAtLessThanEqualOrderByEffectiveAtDesc(anyString(), any(Instant.class)))
                .thenReturn(Optional.empty());

        tapPolicyConfig.refresh();

        assertThat(tapPolicyConfig.pointDailyCap()).isEqualTo(20);
        assertThat(tapPolicyConfig.boxDropBase()).isEqualTo(200);
        assertThat(tapPolicyConfig.boosterDurationSeconds()).isEqualTo(300);
        assertThat(tapPolicyConfig.boosterDailyLimit()).isEqualTo(3);
    }

    @Test
    void keepsDefaultsWhenRepositoryRefreshFails() {
        when(appConfigRepository.findFirstByConfigKeyAndEffectiveAtLessThanEqualOrderByEffectiveAtDesc(anyString(), any(Instant.class)))
                .thenThrow(new RuntimeException("database unavailable"));

        tapPolicyConfig.refresh();

        assertThat(tapPolicyConfig.pointDailyCap()).isEqualTo(20);
        assertThat(tapPolicyConfig.boxDropBase()).isEqualTo(200);
        assertThat(tapPolicyConfig.boosterDurationSeconds()).isEqualTo(300);
        assertThat(tapPolicyConfig.boosterDailyLimit()).isEqualTo(3);
    }
}
