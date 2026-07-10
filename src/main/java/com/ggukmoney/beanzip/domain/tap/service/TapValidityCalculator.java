package com.ggukmoney.beanzip.domain.tap.service;

import com.ggukmoney.beanzip.domain.tap.config.TapPolicyConfig;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class TapValidityCalculator {

    public int calculateAcceptedCount(
            int submittedCount,
            Duration elapsedSinceLastBatch,
            int minuteRemaining,
            int dailyRemaining,
            TapPolicyConfig config
    ) {
        int accepted = Math.min(submittedCount, elapsedBasedCap(elapsedSinceLastBatch, config.minIntervalMs()));
        accepted = Math.min(accepted, Math.max(minuteRemaining, 0));
        accepted = Math.min(accepted, Math.max(dailyRemaining, 0));
        return Math.max(accepted, 0);
    }

    private int elapsedBasedCap(Duration elapsedSinceLastBatch, int minIntervalMs) {
        if (elapsedSinceLastBatch == null) {
            return Integer.MAX_VALUE;
        }
        long elapsedMillis = elapsedSinceLastBatch.toMillis();
        if (elapsedMillis <= 0) {
            return 0;
        }
        return (int) (elapsedMillis / minIntervalMs);
    }
}
