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

    @Test void eachConfiguredProfileMustStayWithinItsApprovedExpectedMultiplier() {
        KeycapPassivePolicyConfig.DEFAULT_VALUES.forEach((key,value) -> {
            if (key.endsWith(".probability"))
                assertThatThrownBy(() -> KeycapPassivePolicyConfig.decode(java.util.Map.of(key,
                        Double.toString(Double.parseDouble(value)+0.01))))
                        .as(key).isInstanceOf(IllegalArgumentException.class);
        });
        assertThatCode(() -> KeycapPassivePolicyConfig.decode(java.util.Map.of())).doesNotThrowAnyException();
    }

    @Test
    void missingRowsKeepEffectsDisabledAndUseApprovedPreviewValues() {
        when(repository.findLatestEffectiveWithHistory(any(), any(), any())).thenReturn(List.of());
        config.refresh();
        assertThat(config.snapshot().enabled()).isFalse();
        assertThat(config.snapshot().policy().effects("radio", 5).shard().probability())
                .isCloseTo(0.28, within(1e-12));
    }

    @Test
    void appliesDatabaseOverridesWithoutChangingAnAlreadyCapturedSnapshot() {
        var captured = config.snapshot();
        when(repository.findLatestEffectiveWithHistory(any(), any(), any())).thenReturn(List.of(
                row("keycap.passive.enabled", "true"),
                row("keycap.passive.main.shard.probability", "0.08"),
                row("keycap.passive.COMMON.capLevel", "10")));
        config.refresh();
        assertThat(config.snapshot().enabled()).isTrue();
        assertThat(config.snapshot().policy().effects("main", 10).shard().probability())
                .isCloseTo(0.08, within(1e-12));
        assertThat(captured.enabled()).isFalse();
        assertThat(captured.policy().effects("main", 10).shard().probability())
                .isCloseTo(0.0510204081632653, within(1e-12));
    }

    @Test
    void invalidGrowthRejectsTheWholeRefreshAndPreservesLastKnownGoodValues() {
        when(repository.findLatestEffectiveWithHistory(any(), any(), any())).thenReturn(List.of(
                row("keycap.passive.main.shard.probability", "0.05")));
        config.refresh();
        var lastKnownGood = config.snapshot();
        when(repository.findLatestEffectiveWithHistory(any(), any(), any())).thenReturn(List.of(
                row("keycap.passive.enabled", "true"),
                row("keycap.passive.COMMON.startStrength", "0.9")));
        config.refresh();
        assertThat(config.snapshot()).isSameAs(lastKnownGood);
        assertThat(config.snapshot().policy().effects("main", 50).shard().probability())
                .isCloseTo(0.05, within(1e-12));
    }

    @Test
    void databaseFailureDoesNotResetAValidOperationalOverride() {
        when(repository.findLatestEffectiveWithHistory(any(), any(), any())).thenReturn(List.of(
                row("keycap.passive.main.shard.probability", "0.05")));
        config.refresh();
        var lastKnownGood = config.snapshot();
        when(repository.findLatestEffectiveWithHistory(any(), any(), any())).thenThrow(new IllegalStateException("DB unavailable"));
        config.refresh();
        assertThat(config.snapshot()).isSameAs(lastKnownGood);
    }

    @Test
    void malformedProbabilityDoesNotActivateEffectsOrPublishAPartialPolicy() {
        when(repository.findLatestEffectiveWithHistory(any(), any(), any())).thenReturn(List.of(
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

    @Test
    void exceedingApprovedCombinedBudgetRejectsWholePolicy() {
        var captured = config.snapshot();
        when(repository.findLatestEffectiveWithHistory(any(), any(), any())).thenReturn(List.of(
                row("keycap.passive.enabled", "true"),
                row("keycap.passive.earth.shard.probability", "0.18")));
        config.refresh();
        assertThat(config.snapshot()).isSameAs(captured);
    }

    @Test
    void oversizedAccrualCannotPublishAnUnpayableIntegerClickCount() {
        var captured = config.snapshot();
        when(repository.findLatestEffectiveWithHistory(any(), any(), any())).thenReturn(List.of(
                row("keycap.passive.COMMON.autoClickCap", "2147483647")));
        config.refresh();
        assertThat(config.snapshot()).isSameAs(captured);
    }

}
