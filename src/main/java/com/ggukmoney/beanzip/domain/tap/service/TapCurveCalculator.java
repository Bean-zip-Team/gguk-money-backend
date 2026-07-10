package com.ggukmoney.beanzip.domain.tap.service;

import com.ggukmoney.beanzip.domain.tap.config.TapPolicyConfig;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Random;

@Component
public class TapCurveCalculator {

    private final Random random = new SecureRandom();

    public int drawNextTarget(int currentValidTapCount, int pointEarnedAmountToday, TapPolicyConfig config) {
        boolean decelerating = pointEarnedAmountToday >= config.decelThresholdPoints();
        int base = decelerating ? config.curveDecelBase() : config.curveGeneralBase();
        double variance = decelerating ? config.curveDecelVariance() : config.curveGeneralVariance();
        int increment = drawUniform(base, variance);
        return currentValidTapCount + increment;
    }

    private int drawUniform(int base, double variance) {
        int lowerBound = (int) Math.round(base * (1 - variance));
        int upperBound = (int) Math.round(base * (1 + variance));
        return lowerBound + random.nextInt(upperBound - lowerBound + 1);
    }
}
