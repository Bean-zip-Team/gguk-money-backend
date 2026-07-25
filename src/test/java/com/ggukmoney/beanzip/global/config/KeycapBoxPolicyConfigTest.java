package com.ggukmoney.beanzip.global.config;

import com.ggukmoney.beanzip.global.config.entity.AppConfig;
import com.ggukmoney.beanzip.global.config.repository.AppConfigRepository;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class KeycapBoxPolicyConfigTest {

    private final AppConfigRepository appConfigRepository = mock(AppConfigRepository.class);
    private final KeycapBoxPolicyConfig config = new KeycapBoxPolicyConfig(appConfigRepository);

    @Test
    void usesDefaultsWhenRowsAreMissing() {
        when(appConfigRepository.findFirstByConfigKeyAndEffectiveAtLessThanEqualOrderByEffectiveAtDesc(any(), any()))
                .thenReturn(Optional.empty());

        config.refresh();

        assertThat(config.openCycleDuration()).isEqualTo(Duration.ofHours(1));
        assertThat(config.freeOpenLimit()).isEqualTo(2);
        assertThat(config.adOpenLimit()).isEqualTo(2);
    }

    @Test
    void resolvesOpenCyclePolicyFromAppConfigRows() {
        when(appConfigRepository.findFirstByConfigKeyAndEffectiveAtLessThanEqualOrderByEffectiveAtDesc(
                eq(KeycapBoxPolicyConfig.KEY_OPEN_CYCLE_DURATION_SECONDS), any(Instant.class)
        )).thenReturn(Optional.of(AppConfig.createFor(
                KeycapBoxPolicyConfig.KEY_OPEN_CYCLE_DURATION_SECONDS,
                "1800",
                Instant.EPOCH
        )));
        when(appConfigRepository.findFirstByConfigKeyAndEffectiveAtLessThanEqualOrderByEffectiveAtDesc(
                eq(KeycapBoxPolicyConfig.KEY_FREE_OPEN_LIMIT), any(Instant.class)
        )).thenReturn(Optional.of(AppConfig.createFor(
                KeycapBoxPolicyConfig.KEY_FREE_OPEN_LIMIT,
                "1",
                Instant.EPOCH
        )));
        when(appConfigRepository.findFirstByConfigKeyAndEffectiveAtLessThanEqualOrderByEffectiveAtDesc(
                eq(KeycapBoxPolicyConfig.KEY_AD_OPEN_LIMIT), any(Instant.class)
        )).thenReturn(Optional.of(AppConfig.createFor(
                KeycapBoxPolicyConfig.KEY_AD_OPEN_LIMIT,
                "3",
                Instant.EPOCH
        )));

        config.refresh();

        assertThat(config.openCycleDuration()).isEqualTo(Duration.ofMinutes(30));
        assertThat(config.freeOpenLimit()).isEqualTo(1);
        assertThat(config.adOpenLimit()).isEqualTo(3);
    }

    @Test
    void usesDefaultWhenOpenCycleDurationIsInvalidOnInitialLoad() {
        when(appConfigRepository.findFirstByConfigKeyAndEffectiveAtLessThanEqualOrderByEffectiveAtDesc(
                eq(KeycapBoxPolicyConfig.KEY_OPEN_CYCLE_DURATION_SECONDS), any(Instant.class)
        )).thenReturn(Optional.of(AppConfig.createFor(
                KeycapBoxPolicyConfig.KEY_OPEN_CYCLE_DURATION_SECONDS,
                "0",
                Instant.EPOCH
        )));

        config.refresh();

        assertThat(config.openCycleDuration()).isEqualTo(Duration.ofHours(1));
    }

    @Test
    void usesDefaultWhenFreeOpenLimitIsInvalidOnInitialLoad() {
        when(appConfigRepository.findFirstByConfigKeyAndEffectiveAtLessThanEqualOrderByEffectiveAtDesc(
                eq(KeycapBoxPolicyConfig.KEY_FREE_OPEN_LIMIT), any(Instant.class)
        )).thenReturn(Optional.of(AppConfig.createFor(
                KeycapBoxPolicyConfig.KEY_FREE_OPEN_LIMIT,
                "-1",
                Instant.EPOCH
        )));

        config.refresh();

        assertThat(config.freeOpenLimit()).isEqualTo(2);
    }

    @Test
    void usesDefaultWhenAdOpenLimitIsInvalidOnInitialLoad() {
        when(appConfigRepository.findFirstByConfigKeyAndEffectiveAtLessThanEqualOrderByEffectiveAtDesc(
                eq(KeycapBoxPolicyConfig.KEY_AD_OPEN_LIMIT), any(Instant.class)
        )).thenReturn(Optional.of(AppConfig.createFor(
                KeycapBoxPolicyConfig.KEY_AD_OPEN_LIMIT,
                "-1",
                Instant.EPOCH
        )));

        config.refresh();

        assertThat(config.adOpenLimit()).isEqualTo(2);
    }

    @Test
    void usesDefaultWhenMalformedPolicyValueIsLoadedInitially() {
        when(appConfigRepository.findFirstByConfigKeyAndEffectiveAtLessThanEqualOrderByEffectiveAtDesc(
                eq(KeycapBoxPolicyConfig.KEY_OPEN_CYCLE_DURATION_SECONDS), any(Instant.class)
        )).thenReturn(Optional.of(AppConfig.createFor(
                KeycapBoxPolicyConfig.KEY_OPEN_CYCLE_DURATION_SECONDS,
                "not-a-number",
                Instant.EPOCH
        )));

        config.refresh();

        assertThat(config.openCycleDuration()).isEqualTo(Duration.ofHours(1));
    }

    @Test
    void usesDefaultWhenPolicyValueIsDecimal() {
        when(appConfigRepository.findFirstByConfigKeyAndEffectiveAtLessThanEqualOrderByEffectiveAtDesc(
                eq(KeycapBoxPolicyConfig.KEY_FREE_OPEN_LIMIT), any(Instant.class)
        )).thenReturn(Optional.of(AppConfig.createFor(
                KeycapBoxPolicyConfig.KEY_FREE_OPEN_LIMIT,
                "1.5",
                Instant.EPOCH
        )));

        config.refresh();

        assertThat(config.freeOpenLimit()).isEqualTo(2);
    }

    @Test
    void usesDefaultWhenPolicyValueExceedsIntegerRange() {
        when(appConfigRepository.findFirstByConfigKeyAndEffectiveAtLessThanEqualOrderByEffectiveAtDesc(
                eq(KeycapBoxPolicyConfig.KEY_AD_OPEN_LIMIT), any(Instant.class)
        )).thenReturn(Optional.of(AppConfig.createFor(
                KeycapBoxPolicyConfig.KEY_AD_OPEN_LIMIT,
                "2147483648",
                Instant.EPOCH
        )));

        config.refresh();

        assertThat(config.adOpenLimit()).isEqualTo(2);
    }

    @Test
    void keepsLastKnownGoodWhenRefreshReceivesMalformedPolicyValue() {
        when(appConfigRepository.findFirstByConfigKeyAndEffectiveAtLessThanEqualOrderByEffectiveAtDesc(
                eq(KeycapBoxPolicyConfig.KEY_OPEN_CYCLE_DURATION_SECONDS), any(Instant.class)
        )).thenReturn(
                Optional.of(AppConfig.createFor(
                        KeycapBoxPolicyConfig.KEY_OPEN_CYCLE_DURATION_SECONDS,
                        "1800",
                        Instant.EPOCH
                )),
                Optional.of(AppConfig.createFor(
                        KeycapBoxPolicyConfig.KEY_OPEN_CYCLE_DURATION_SECONDS,
                        "not-a-number",
                        Instant.EPOCH
                ))
        );

        config.refresh();
        config.refresh();

        assertThat(config.openCycleDuration()).isEqualTo(Duration.ofMinutes(30));
    }

    @Test
    void appliesValidPolicyValuesWhenOnlyOneRefreshValueIsMalformed() {
        when(appConfigRepository.findFirstByConfigKeyAndEffectiveAtLessThanEqualOrderByEffectiveAtDesc(
                eq(KeycapBoxPolicyConfig.KEY_OPEN_CYCLE_DURATION_SECONDS), any(Instant.class)
        )).thenReturn(Optional.of(AppConfig.createFor(
                KeycapBoxPolicyConfig.KEY_OPEN_CYCLE_DURATION_SECONDS,
                "not-a-number",
                Instant.EPOCH
        )));
        when(appConfigRepository.findFirstByConfigKeyAndEffectiveAtLessThanEqualOrderByEffectiveAtDesc(
                eq(KeycapBoxPolicyConfig.KEY_FREE_OPEN_LIMIT), any(Instant.class)
        )).thenReturn(Optional.of(AppConfig.createFor(
                KeycapBoxPolicyConfig.KEY_FREE_OPEN_LIMIT,
                "1",
                Instant.EPOCH
        )));
        when(appConfigRepository.findFirstByConfigKeyAndEffectiveAtLessThanEqualOrderByEffectiveAtDesc(
                eq(KeycapBoxPolicyConfig.KEY_AD_OPEN_LIMIT), any(Instant.class)
        )).thenReturn(Optional.of(AppConfig.createFor(
                KeycapBoxPolicyConfig.KEY_AD_OPEN_LIMIT,
                "3",
                Instant.EPOCH
        )));

        config.refresh();

        assertThat(config.openCycleDuration()).isEqualTo(Duration.ofHours(1));
        assertThat(config.freeOpenLimit()).isEqualTo(1);
        assertThat(config.adOpenLimit()).isEqualTo(3);
    }
}
