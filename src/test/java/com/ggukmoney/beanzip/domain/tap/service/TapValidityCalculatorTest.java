package com.ggukmoney.beanzip.domain.tap.service;

import com.ggukmoney.beanzip.domain.tap.config.TapPolicyConfig;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TapValidityCalculatorTest {

    private final TapValidityCalculator calculator = new TapValidityCalculator();

    @Test
    void capsBySubmittedCountWhenNothingElseIsBinding() {
        TapPolicyConfig config = configWithMinInterval(80);

        int accepted = calculator.calculateAcceptedCount(50, Duration.ofSeconds(30), 400, 10000, config);

        assertThat(accepted).isEqualTo(50);
    }

    @Test
    void capsByElapsedTimeWhenSubmittedCountImpliesFasterThanMinInterval() {
        TapPolicyConfig config = configWithMinInterval(80);

        int accepted = calculator.calculateAcceptedCount(100, Duration.ofMillis(800), 400, 10000, config);

        assertThat(accepted).isEqualTo(10);
    }

    @Test
    void capsByMinuteRemaining() {
        TapPolicyConfig config = configWithMinInterval(80);

        int accepted = calculator.calculateAcceptedCount(100, Duration.ofSeconds(30), 5, 10000, config);

        assertThat(accepted).isEqualTo(5);
    }

    @Test
    void capsByDailyRemaining() {
        TapPolicyConfig config = configWithMinInterval(80);

        int accepted = calculator.calculateAcceptedCount(100, Duration.ofSeconds(30), 400, 3, config);

        assertThat(accepted).isEqualTo(3);
    }

    @Test
    void returnsZeroWhenDailyRemainingIsAlreadyNegative() {
        TapPolicyConfig config = configWithMinInterval(80);

        int accepted = calculator.calculateAcceptedCount(100, Duration.ofSeconds(30), 400, -5, config);

        assertThat(accepted).isZero();
    }

    @Test
    void treatsNullElapsedAsNoTimeBasedCapForFirstEverBatch() {
        TapPolicyConfig config = configWithMinInterval(80);

        int accepted = calculator.calculateAcceptedCount(100, null, 400, 10000, config);

        assertThat(accepted).isEqualTo(100);
    }

    private TapPolicyConfig configWithMinInterval(int minIntervalMs) {
        TapPolicyConfig config = mock(TapPolicyConfig.class);
        when(config.minIntervalMs()).thenReturn(minIntervalMs);
        return config;
    }
}
