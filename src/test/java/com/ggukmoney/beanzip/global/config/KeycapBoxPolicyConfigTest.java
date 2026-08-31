package com.ggukmoney.beanzip.global.config;

import com.ggukmoney.beanzip.global.config.entity.AppConfig;
import com.ggukmoney.beanzip.global.config.repository.AppConfigRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KeycapBoxPolicyConfigTest {

    private final AppConfigRepository appConfigRepository = mock(AppConfigRepository.class);
    private final KeycapBoxPolicyConfig config = new KeycapBoxPolicyConfig(new AppConfigBatchLoader(appConfigRepository));

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
    void managesOnlySharedCyclePolicyKeys() {
        assertThat(KeycapBoxPolicyConfig.DEFAULT_VALUES)
                .containsOnlyKeys(
                        KeycapBoxPolicyConfig.KEY_OPEN_CYCLE_DURATION_SECONDS,
                        KeycapBoxPolicyConfig.KEY_FREE_OPEN_LIMIT,
                        KeycapBoxPolicyConfig.KEY_AD_OPEN_LIMIT
                )
                .containsEntry(KeycapBoxPolicyConfig.KEY_OPEN_CYCLE_DURATION_SECONDS, "3600")
                .containsEntry(KeycapBoxPolicyConfig.KEY_FREE_OPEN_LIMIT, "2")
                .containsEntry(KeycapBoxPolicyConfig.KEY_AD_OPEN_LIMIT, "6");
    }

    @Test
    void usesDefaultsWhenRowsAreMissing() {
        when(appConfigRepository.findFirstByConfigKeyAndEffectiveAtLessThanEqualOrderByEffectiveAtDesc(any(), any()))
                .thenReturn(Optional.empty());

        config.refresh();

        assertThat(config.openCycleDuration()).isEqualTo(Duration.ofHours(1));
        assertThat(config.freeOpenLimit()).isEqualTo(2);
        assertThat(config.adOpenLimit()).isEqualTo(6);
        verify(appConfigRepository).findLatestEffectiveByConfigKeys(
                org.mockito.ArgumentMatchers.eq(KeycapBoxPolicyConfig.DEFAULT_VALUES.keySet()),
                any(Instant.class)
        );
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

        assertThat(config.adOpenLimit()).isEqualTo(6);
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

        assertThat(config.adOpenLimit()).isEqualTo(6);
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
