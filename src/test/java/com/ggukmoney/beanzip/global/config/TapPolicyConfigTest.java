package com.ggukmoney.beanzip.global.config;

import com.ggukmoney.beanzip.global.config.entity.AppConfig;
import com.ggukmoney.beanzip.global.config.repository.AppConfigRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Collection;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TapPolicyConfigTest {

    private final AppConfigRepository appConfigRepository = mock(AppConfigRepository.class);
    private final TapPolicyConfig tapPolicyConfig = new TapPolicyConfig(new AppConfigBatchLoader(appConfigRepository));

    @BeforeEach
    void adaptExistingSingleKeyFixturesToBatchQuery() {
        when(appConfigRepository.findLatestEffectiveByConfigKeys(any(), any(Instant.class)))
                .thenAnswer(invocation -> {
                    Collection<String> keys = invocation.getArgument(0);
                    Instant now = invocation.getArgument(1);
                    return keys.stream()
                            .map(key -> appConfigRepository
                                    .findFirstByConfigKeyAndEffectiveAtLessThanEqualOrderByEffectiveAtDesc(key, now))
                            .flatMap(Optional::stream)
                            .toList();
                });
    }

    @Test
    void usesDefaultsWhenRowsAreMissing() {
        when(appConfigRepository.findFirstByConfigKeyAndEffectiveAtLessThanEqualOrderByEffectiveAtDesc(anyString(), any(Instant.class)))
                .thenReturn(Optional.empty());

        tapPolicyConfig.refresh();

        assertThat(tapPolicyConfig.pointDailyCap()).isEqualTo(150);
        assertThat(tapPolicyConfig.boxSessionStep1()).isEqualTo(25);
        assertThat(tapPolicyConfig.boxSessionTailStep()).isEqualTo(180);
        assertThat(tapPolicyConfig.boosterDurationSeconds()).isEqualTo(300);
        assertThat(tapPolicyConfig.boosterDailyLimit()).isEqualTo(3);
        verify(appConfigRepository).findLatestEffectiveByConfigKeys(
                org.mockito.ArgumentMatchers.eq(TapPolicyConfig.DEFAULT_VALUES.keySet()),
                any(Instant.class)
        );
    }

    @Test
    void keepsDefaultsWhenRepositoryRefreshFails() {
        when(appConfigRepository.findFirstByConfigKeyAndEffectiveAtLessThanEqualOrderByEffectiveAtDesc(anyString(), any(Instant.class)))
                .thenThrow(new RuntimeException("database unavailable"));

        tapPolicyConfig.refresh();

        assertThat(tapPolicyConfig.pointDailyCap()).isEqualTo(150);
        assertThat(tapPolicyConfig.boxSessionStep1()).isEqualTo(25);
        assertThat(tapPolicyConfig.boxSessionTailStep()).isEqualTo(180);
        assertThat(tapPolicyConfig.boosterDurationSeconds()).isEqualTo(300);
        assertThat(tapPolicyConfig.boosterDailyLimit()).isEqualTo(3);
    }

    @Test
    void appliesValidKeyWhileKeepingInvalidAndMissingKeys() {
        when(appConfigRepository.findFirstByConfigKeyAndEffectiveAtLessThanEqualOrderByEffectiveAtDesc(
                org.mockito.ArgumentMatchers.eq(TapPolicyConfig.KEY_POINT_DAILY_CAP), any(Instant.class)
        )).thenReturn(Optional.of(AppConfig.createFor(
                TapPolicyConfig.KEY_POINT_DAILY_CAP, "not-an-int", Instant.EPOCH
        )));
        when(appConfigRepository.findFirstByConfigKeyAndEffectiveAtLessThanEqualOrderByEffectiveAtDesc(
                org.mockito.ArgumentMatchers.eq(TapPolicyConfig.KEY_BOX_SESSION_STEP_1), any(Instant.class)
        )).thenReturn(Optional.of(AppConfig.createFor(
                TapPolicyConfig.KEY_BOX_SESSION_STEP_1, "30", Instant.EPOCH
        )));
        when(appConfigRepository.findFirstByConfigKeyAndEffectiveAtLessThanEqualOrderByEffectiveAtDesc(
                org.mockito.ArgumentMatchers.eq(TapPolicyConfig.KEY_RATE_LIMIT_ENABLED), any(Instant.class)
        )).thenReturn(Optional.of(AppConfig.createFor(
                TapPolicyConfig.KEY_RATE_LIMIT_ENABLED, "not-a-boolean", Instant.EPOCH
        )));

        tapPolicyConfig.refresh();

        assertThat(tapPolicyConfig.pointDailyCap()).isEqualTo(150);
        assertThat(tapPolicyConfig.boxSessionStep1()).isEqualTo(30);
        assertThat(tapPolicyConfig.rateLimitEnabled()).isFalse();
    }

    @Test
    void keepsLastKnownGoodForInvalidKeyAndUpdatesOtherValidKey() {
        when(appConfigRepository.findFirstByConfigKeyAndEffectiveAtLessThanEqualOrderByEffectiveAtDesc(
                org.mockito.ArgumentMatchers.eq(TapPolicyConfig.KEY_POINT_DAILY_CAP), any(Instant.class)
        )).thenReturn(
                Optional.of(AppConfig.createFor(TapPolicyConfig.KEY_POINT_DAILY_CAP, "200", Instant.EPOCH)),
                Optional.of(AppConfig.createFor(TapPolicyConfig.KEY_POINT_DAILY_CAP, "invalid", Instant.EPOCH))
        );
        when(appConfigRepository.findFirstByConfigKeyAndEffectiveAtLessThanEqualOrderByEffectiveAtDesc(
                org.mockito.ArgumentMatchers.eq(TapPolicyConfig.KEY_BOX_SESSION_STEP_1), any(Instant.class)
        )).thenReturn(Optional.of(AppConfig.createFor(
                TapPolicyConfig.KEY_BOX_SESSION_STEP_1, "31", Instant.EPOCH
        )));

        tapPolicyConfig.refresh();
        tapPolicyConfig.refresh();

        assertThat(tapPolicyConfig.pointDailyCap()).isEqualTo(200);
        assertThat(tapPolicyConfig.boxSessionStep1()).isEqualTo(31);
    }
}
