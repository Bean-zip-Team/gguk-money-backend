package com.ggukmoney.beanzip.global.config;

import com.ggukmoney.beanzip.global.config.entity.AppConfig;
import com.ggukmoney.beanzip.global.config.repository.AppConfigRepository;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class KeycapPassivePolicyConfigTest {
    private final AppConfigRepository repository = mock(AppConfigRepository.class);
    private final KeycapPassivePolicyConfig config = new KeycapPassivePolicyConfig(new AppConfigBatchLoader(repository));

    @Test
    void missingRowsKeepEffectsDisabledAndUseApprovedPreviewValues() {
        when(repository.findLatestEffectiveByConfigKeys(any(), any())).thenReturn(List.of());
        config.refresh();
        assertThat(config.snapshot().enabled()).isFalse();
        assertThat(config.snapshot().policy().effects("radio", 5).shard().probability())
                .isCloseTo(0.28, within(1e-12));
    }

    @Test
    void appliesDatabaseOverridesWithoutChangingAnAlreadyCapturedSnapshot() {
        var captured = config.snapshot();
        when(repository.findLatestEffectiveByConfigKeys(any(), any())).thenReturn(List.of(
                row("keycap.passive.enabled", "true"),
                row("keycap.passive.main.shard.probability", "0.2"),
                row("keycap.passive.COMMON.capLevel", "10")));
        config.refresh();
        assertThat(config.snapshot().enabled()).isTrue();
        assertThat(config.snapshot().policy().effects("main", 10).shard().probability())
                .isCloseTo(0.2, within(1e-12));
        assertThat(captured.enabled()).isFalse();
        assertThat(captured.policy().effects("main", 10).shard().probability())
                .isCloseTo(0.0510204081632653, within(1e-12));
    }

    @Test
    void invalidGrowthRejectsTheWholeRefreshAndPreservesLastKnownGoodValues() {
        when(repository.findLatestEffectiveByConfigKeys(any(), any())).thenReturn(List.of(
                row("keycap.passive.main.shard.probability", "0.3")));
        config.refresh();
        var lastKnownGood = config.snapshot();
        when(repository.findLatestEffectiveByConfigKeys(any(), any())).thenReturn(List.of(
                row("keycap.passive.enabled", "true"),
                row("keycap.passive.COMMON.startStrength", "0.9")));
        config.refresh();
        assertThat(config.snapshot()).isSameAs(lastKnownGood);
        assertThat(config.snapshot().policy().effects("main", 50).shard().probability())
                .isCloseTo(0.3, within(1e-12));
    }

    @Test
    void databaseFailureDoesNotResetAValidOperationalOverride() {
        when(repository.findLatestEffectiveByConfigKeys(any(), any())).thenReturn(List.of(
                row("keycap.passive.main.shard.probability", "0.3")));
        config.refresh();
        var lastKnownGood = config.snapshot();
        when(repository.findLatestEffectiveByConfigKeys(any(), any())).thenThrow(new IllegalStateException("DB unavailable"));
        config.refresh();
        assertThat(config.snapshot()).isSameAs(lastKnownGood);
    }

    @Test
    void malformedProbabilityDoesNotActivateEffectsOrPublishAPartialPolicy() {
        when(repository.findLatestEffectiveByConfigKeys(any(), any())).thenReturn(List.of(
                row("keycap.passive.enabled", "true"),
                row("keycap.passive.main.shard.probability", "NaN")));
        config.refresh();
        assertThat(config.snapshot().enabled()).isFalse();
        assertThat(config.snapshot().policy().effects("main", 50).shard().probability())
                .isCloseTo(0.1, within(1e-12));
    }

    private AppConfig row(String key, String value) {
        return AppConfig.createFor(key, value, Instant.EPOCH);
    }
}
