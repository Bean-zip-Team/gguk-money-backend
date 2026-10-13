package com.ggukmoney.beanzip.global.config;

import com.ggukmoney.beanzip.global.config.entity.AppConfig;
import com.ggukmoney.beanzip.global.config.repository.AppConfigRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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

    @Test
    void missingAutoClickRowsKeepTheApprovedDailyRatesAndIndependentCaps() {
        when(repository.findLatestEffectiveByConfigKeys(any(), any())).thenReturn(List.of());
        config.refresh();
        var policy = config.snapshot().policy();
        assertThat(config.snapshot().enabled()).isFalse();
        assertThat(policy.effects("cheer", 1).autoClicksPerDay()).isEqualTo(60);
        assertThat(policy.effects("pinkjelly", 1).autoClicksPerDay()).isEqualTo(100);
        assertThat(policy.effects("moon", 1).autoClicksPerDay()).isEqualTo(160);
        assertThat(policy.effects("radio", 1).autoClicksPerDay()).isEqualTo(300);
        assertThat(policy.effects("cheer", 11).autoClicksPerDay()).isEqualTo(180);
        assertThat(policy.effects("pinkjelly", 11).autoClicksPerDay()).isEqualTo(300);
        assertThat(policy.effects("moon", 11).autoClicksPerDay()).isEqualTo(480);
        assertThat(policy.effects("radio", 11).autoClicksPerDay()).isEqualTo(900);
    }

    @Test
    void autoClickOverridesAffectGrowthAndCapWithoutChangingACapturedSnapshot() {
        var captured = config.snapshot();
        when(repository.findLatestEffectiveByConfigKeys(any(), any())).thenReturn(List.of(
                row("keycap.passive.COMMON.autoClickBase", "40"),
                row("keycap.passive.COMMON.autoClickPerLevel", "7"),
                row("keycap.passive.COMMON.autoClickCap", "68")));
        config.refresh();
        var policy = config.snapshot().policy();
        assertThat(policy.effects("cheer", 1).autoClicksPerDay()).isEqualTo(40);
        assertThat(policy.effects("cheer", 4).autoClicksPerDay()).isEqualTo(61);
        assertThat(policy.effects("cheer", 5).autoClicksPerDay()).isEqualTo(68);
        assertThat(captured.policy().effects("cheer", 4).autoClicksPerDay()).isEqualTo(96);
        assertThat(config.snapshot().enabled()).isFalse();
    }

    @ParameterizedTest
    @CsvSource({"autoClickBase,-1", "autoClickPerLevel,-1", "autoClickCap,59", "autoClickBase,not-a-number"})
    void invalidAutoClickValuesRejectActivationAndPreserveTheLastGoodSnapshot(String key, String value) {
        when(repository.findLatestEffectiveByConfigKeys(any(), any())).thenReturn(List.of(
                row("keycap.passive.COMMON.autoClickBase", "80")));
        config.refresh();
        var lastKnownGood = config.snapshot();
        when(repository.findLatestEffectiveByConfigKeys(any(), any())).thenReturn(List.of(
                row("keycap.passive.enabled", "true"),
                row("keycap.passive.COMMON." + key, value)));
        config.refresh();
        assertThat(config.snapshot()).isSameAs(lastKnownGood);
        assertThat(config.snapshot().enabled()).isFalse();
        assertThat(config.snapshot().policy().effects("cheer", 1).autoClicksPerDay()).isEqualTo(80);
    }

    private AppConfig row(String key, String value) {
        return AppConfig.createFor(key, value, Instant.EPOCH);
    }
}
