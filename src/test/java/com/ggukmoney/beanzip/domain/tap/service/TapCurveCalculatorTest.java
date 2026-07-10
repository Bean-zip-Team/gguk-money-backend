package com.ggukmoney.beanzip.domain.tap.service;

import com.ggukmoney.beanzip.domain.tap.config.TapPolicyConfig;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TapCurveCalculatorTest {

    private final TapCurveCalculator calculator = new TapCurveCalculator();

    @Test
    void drawsGeneralCurveTargetWithinConfiguredVarianceWhenBelowDecelThreshold() {
        TapPolicyConfig config = mock(TapPolicyConfig.class);
        when(config.decelThresholdPoints()).thenReturn(7);
        when(config.curveGeneralBase()).thenReturn(300);
        when(config.curveGeneralVariance()).thenReturn(0.10);

        for (int i = 0; i < 200; i++) {
            int target = calculator.drawNextTarget(1000, 6, config);
            assertThat(target - 1000).isBetween(270, 330);
        }
    }

    @Test
    void drawsDecelCurveTargetWithinConfiguredVarianceWhenAtOrAboveDecelThreshold() {
        TapPolicyConfig config = mock(TapPolicyConfig.class);
        when(config.decelThresholdPoints()).thenReturn(7);
        when(config.curveDecelBase()).thenReturn(600);
        when(config.curveDecelVariance()).thenReturn(0.05);

        for (int i = 0; i < 200; i++) {
            int target = calculator.drawNextTarget(5000, 7, config);
            assertThat(target - 5000).isBetween(570, 630);
        }
    }
}
