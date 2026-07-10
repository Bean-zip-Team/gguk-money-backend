package com.ggukmoney.beanzip.domain.tap.config;

import com.ggukmoney.beanzip.domain.config.repository.AppConfigRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * First real consumer of {@code AppConfig}. Caches tunable tap-domain policy values in memory
 * and refreshes them periodically so config changes can be rolled out without a redeploy.
 */
@Component
@RequiredArgsConstructor
public class TapPolicyConfig {

    private static final Logger log = LoggerFactory.getLogger(TapPolicyConfig.class);

    public static final String KEY_MIN_INTERVAL_MS = "tap.validity.minIntervalMs";
    public static final String KEY_MAX_PER_MINUTE = "tap.validity.maxPerMinute";
    public static final String KEY_MAX_PER_DAY = "tap.validity.maxPerDay";
    public static final String KEY_CURVE_GENERAL_BASE = "tap.curve.general.base";
    public static final String KEY_CURVE_GENERAL_VARIANCE = "tap.curve.general.variance";
    public static final String KEY_CURVE_DECEL_BASE = "tap.curve.decel.base";
    public static final String KEY_CURVE_DECEL_VARIANCE = "tap.curve.decel.variance";
    public static final String KEY_DECEL_THRESHOLD_POINTS = "tap.curve.decelThresholdPoints";
    public static final String KEY_POINT_DAILY_CAP = "tap.point.dailyCap";
    public static final String KEY_BOT_SAMPLE_SIZE = "tap.bot.sampleSize";
    public static final String KEY_BOT_STDDEV_THRESHOLD_MS = "tap.bot.stddevThresholdMs";
    public static final String KEY_RATE_LIMIT_CAPACITY = "tap.rateLimit.capacity";
    public static final String KEY_RATE_LIMIT_REFILL_PER_SECOND = "tap.rateLimit.refillPerSecond";

    public static final Map<String, String> DEFAULT_VALUES = Map.ofEntries(
            Map.entry(KEY_MIN_INTERVAL_MS, "80"),
            Map.entry(KEY_MAX_PER_MINUTE, "420"),
            Map.entry(KEY_MAX_PER_DAY, "12000"),
            Map.entry(KEY_CURVE_GENERAL_BASE, "300"),
            Map.entry(KEY_CURVE_GENERAL_VARIANCE, "0.10"),
            Map.entry(KEY_CURVE_DECEL_BASE, "600"),
            Map.entry(KEY_CURVE_DECEL_VARIANCE, "0.05"),
            Map.entry(KEY_DECEL_THRESHOLD_POINTS, "7"),
            Map.entry(KEY_POINT_DAILY_CAP, "20"),
            Map.entry(KEY_BOT_SAMPLE_SIZE, "10"),
            Map.entry(KEY_BOT_STDDEV_THRESHOLD_MS, "12"),
            Map.entry(KEY_RATE_LIMIT_CAPACITY, "8"),
            Map.entry(KEY_RATE_LIMIT_REFILL_PER_SECOND, "0.125")
    );

    private final AppConfigRepository appConfigRepository;

    private final Map<String, String> cache = new ConcurrentHashMap<>();

    @PostConstruct
    @Scheduled(fixedRate = 60_000)
    public void refresh() {
        try {
            Instant now = Instant.now();
            for (String key : DEFAULT_VALUES.keySet()) {
                appConfigRepository.findFirstByConfigKeyAndEffectiveAtLessThanEqualOrderByEffectiveAtDesc(key, now)
                        .ifPresent(config -> cache.put(key, config.getConfigValue()));
            }
        } catch (RuntimeException exception) {
            // Config refresh must never crash startup or a scheduled tick; DEFAULT_VALUES already
            // covers every key, so a stale/empty cache degrades gracefully.
            log.warn("Failed to refresh tap policy config from AppConfig; falling back to defaults", exception);
        }
    }

    public int minIntervalMs() {
        return getInt(KEY_MIN_INTERVAL_MS);
    }

    public int maxPerMinute() {
        return getInt(KEY_MAX_PER_MINUTE);
    }

    public int maxPerDay() {
        return getInt(KEY_MAX_PER_DAY);
    }

    public int curveGeneralBase() {
        return getInt(KEY_CURVE_GENERAL_BASE);
    }

    public double curveGeneralVariance() {
        return getDouble(KEY_CURVE_GENERAL_VARIANCE);
    }

    public int curveDecelBase() {
        return getInt(KEY_CURVE_DECEL_BASE);
    }

    public double curveDecelVariance() {
        return getDouble(KEY_CURVE_DECEL_VARIANCE);
    }

    public int decelThresholdPoints() {
        return getInt(KEY_DECEL_THRESHOLD_POINTS);
    }

    public int pointDailyCap() {
        return getInt(KEY_POINT_DAILY_CAP);
    }

    public int botSampleSize() {
        return getInt(KEY_BOT_SAMPLE_SIZE);
    }

    public double botStddevThresholdMs() {
        return getDouble(KEY_BOT_STDDEV_THRESHOLD_MS);
    }

    public int rateLimitCapacity() {
        return getInt(KEY_RATE_LIMIT_CAPACITY);
    }

    public double rateLimitRefillPerSecond() {
        return getDouble(KEY_RATE_LIMIT_REFILL_PER_SECOND);
    }

    private int getInt(String key) {
        return Integer.parseInt(resolve(key).trim());
    }

    private double getDouble(String key) {
        return Double.parseDouble(resolve(key).trim());
    }

    private String resolve(String key) {
        return cache.getOrDefault(key, DEFAULT_VALUES.get(key));
    }
}
