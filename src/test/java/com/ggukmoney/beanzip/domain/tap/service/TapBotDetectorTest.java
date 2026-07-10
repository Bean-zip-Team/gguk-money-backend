package com.ggukmoney.beanzip.domain.tap.service;

import com.ggukmoney.beanzip.domain.tap.config.TapPolicyConfig;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TapBotDetectorTest {

    private final TapBotDetector detector = new TapBotDetector();

    @Test
    void flagsPerfectlyRegularIntervalsAsSuspicious() {
        TapPolicyConfig config = configWithThreshold(12.0);
        Instant base = Instant.parse("2026-07-10T00:00:00Z");
        List<Instant> timestamps = List.of(
                base,
                base.minusMillis(8000),
                base.minusMillis(16000),
                base.minusMillis(24000)
        );

        assertThat(detector.isSuspicious(timestamps, config)).isTrue();
    }

    @Test
    void doesNotFlagJitteredIntervalsAsSuspicious() {
        TapPolicyConfig config = configWithThreshold(12.0);
        Instant base = Instant.parse("2026-07-10T00:00:00Z");
        List<Instant> timestamps = List.of(
                base,
                base.minusMillis(7200),
                base.minusMillis(16500),
                base.minusMillis(22000)
        );

        assertThat(detector.isSuspicious(timestamps, config)).isFalse();
    }

    @Test
    void doesNotFlagWhenSampleSizeTooSmall() {
        TapPolicyConfig config = configWithThreshold(12.0);
        List<Instant> timestamps = List.of(Instant.now(), Instant.now().minusSeconds(8));

        assertThat(detector.isSuspicious(timestamps, config)).isFalse();
    }

    private TapPolicyConfig configWithThreshold(double thresholdMs) {
        TapPolicyConfig config = mock(TapPolicyConfig.class);
        when(config.botStddevThresholdMs()).thenReturn(thresholdMs);
        return config;
    }
}
