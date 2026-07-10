package com.ggukmoney.beanzip.domain.tap.service;

import com.ggukmoney.beanzip.domain.tap.config.TapPolicyConfig;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

@Component
public class TapBotDetector {

    private static final int MIN_SAMPLE_SIZE = 3;

    /**
     * timestampsMostRecentFirst must be ordered newest-to-oldest (TapBatch.createdAt DESC).
     */
    public boolean isSuspicious(List<Instant> timestampsMostRecentFirst, TapPolicyConfig config) {
        if (timestampsMostRecentFirst.size() < MIN_SAMPLE_SIZE) {
            return false;
        }

        double[] gapsMillis = new double[timestampsMostRecentFirst.size() - 1];
        for (int i = 0; i < gapsMillis.length; i++) {
            long gap = timestampsMostRecentFirst.get(i).toEpochMilli() - timestampsMostRecentFirst.get(i + 1).toEpochMilli();
            gapsMillis[i] = Math.abs(gap);
        }

        double stddev = standardDeviation(gapsMillis);
        return stddev < config.botStddevThresholdMs();
    }

    private double standardDeviation(double[] values) {
        double mean = 0;
        for (double value : values) {
            mean += value;
        }
        mean /= values.length;

        double variance = 0;
        for (double value : values) {
            variance += Math.pow(value - mean, 2);
        }
        variance /= values.length;

        return Math.sqrt(variance);
    }
}
