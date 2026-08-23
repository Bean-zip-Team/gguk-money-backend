package com.ggukmoney.beanzip.global.config;

import com.ggukmoney.beanzip.global.config.repository.AppConfigRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
public class TapPolicyConfig {

    private static final Logger log = LoggerFactory.getLogger(TapPolicyConfig.class);

    public static final String KEY_MIN_INTERVAL_MS = "tap.validity.minIntervalMs";
    public static final String KEY_MAX_PER_MINUTE = "tap.validity.maxPerMinute";
    public static final String KEY_MAX_PER_DAY = "tap.validity.maxPerDay";
    public static final String KEY_CURVE_GENERAL_BASE = "tap.curve.general.base";
    public static final String KEY_CURVE_GENERAL_VARIANCE = "tap.curve.general.variance";
    public static final String KEY_POINT_DAILY_CAP = "tap.point.dailyCap";
    public static final String KEY_BOT_ENABLED = "tap.bot.enabled";
    public static final String KEY_BOT_SAMPLE_SIZE = "tap.bot.sampleSize";
    public static final String KEY_BOT_STDDEV_THRESHOLD_MS = "tap.bot.stddevThresholdMs";
    public static final String KEY_RATE_LIMIT_ENABLED = "tap.rateLimit.enabled";
    public static final String KEY_RATE_LIMIT_CAPACITY = "tap.rateLimit.capacity";
    public static final String KEY_RATE_LIMIT_REFILL_PER_SECOND = "tap.rateLimit.refillPerSecond";
    public static final String KEY_BOX_SESSION_STEP_1 = "tap.box.session.step1";
    public static final String KEY_BOX_SESSION_STEP_2 = "tap.box.session.step2";
    public static final String KEY_BOX_SESSION_STEP_3 = "tap.box.session.step3";
    public static final String KEY_BOX_SESSION_STEP_4 = "tap.box.session.step4";
    public static final String KEY_BOX_SESSION_STEP_5 = "tap.box.session.step5";
    public static final String KEY_BOX_SESSION_TAIL_STEP = "tap.box.session.tailStep";
    public static final String KEY_BOX_SESSION_IDLE_TIMEOUT_SECONDS = "tap.box.session.idleTimeoutSeconds";
    public static final String KEY_BOOSTER_DURATION_SECONDS = "tap.booster.durationSeconds";
    public static final String KEY_BOOSTER_DAILY_LIMIT = "tap.booster.dailyLimit";
    public static final String KEY_BOOSTER_LIMIT_WINDOW_SECONDS = "tap.booster.limitWindowSeconds";

    public static final Map<String, String> DEFAULT_VALUES = Map.ofEntries(
            Map.entry(KEY_MIN_INTERVAL_MS, "80"),
            Map.entry(KEY_MAX_PER_MINUTE, "420"),
            Map.entry(KEY_MAX_PER_DAY, "3000"),
            Map.entry(KEY_CURVE_GENERAL_BASE, "20"),
            Map.entry(KEY_CURVE_GENERAL_VARIANCE, "0.25"),
            Map.entry(KEY_POINT_DAILY_CAP, "150"),
            Map.entry(KEY_BOT_ENABLED, "false"),
            Map.entry(KEY_BOT_SAMPLE_SIZE, "10"),
            Map.entry(KEY_BOT_STDDEV_THRESHOLD_MS, "12"),
            Map.entry(KEY_RATE_LIMIT_ENABLED, "false"),
            Map.entry(KEY_RATE_LIMIT_CAPACITY, "8"),
            Map.entry(KEY_RATE_LIMIT_REFILL_PER_SECOND, "0.125"),
            Map.entry(KEY_BOX_SESSION_STEP_1, "25"),
            Map.entry(KEY_BOX_SESSION_STEP_2, "35"),
            Map.entry(KEY_BOX_SESSION_STEP_3, "50"),
            Map.entry(KEY_BOX_SESSION_STEP_4, "70"),
            Map.entry(KEY_BOX_SESSION_STEP_5, "100"),
            Map.entry(KEY_BOX_SESSION_TAIL_STEP, "180"),
            Map.entry(KEY_BOX_SESSION_IDLE_TIMEOUT_SECONDS, "1800"),
            Map.entry(KEY_BOOSTER_DURATION_SECONDS, "300"),
            Map.entry(KEY_BOOSTER_DAILY_LIMIT, "3"),
            Map.entry(KEY_BOOSTER_LIMIT_WINDOW_SECONDS, "86400")
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




    public int pointDailyCap() {
        return getInt(KEY_POINT_DAILY_CAP);
    }

    public boolean botDetectionEnabled() {
        return getBoolean(KEY_BOT_ENABLED);
    }

    public int botSampleSize() {
        return getInt(KEY_BOT_SAMPLE_SIZE);
    }

    public double botStddevThresholdMs() {
        return getDouble(KEY_BOT_STDDEV_THRESHOLD_MS);
    }

    public boolean rateLimitEnabled() {
        return getBoolean(KEY_RATE_LIMIT_ENABLED);
    }

    public int rateLimitCapacity() {
        return getInt(KEY_RATE_LIMIT_CAPACITY);
    }

    public double rateLimitRefillPerSecond() {
        return getDouble(KEY_RATE_LIMIT_REFILL_PER_SECOND);
    }

    public int boxSessionStep1() {
        return getInt(KEY_BOX_SESSION_STEP_1);
    }

    public int boxSessionStep2() {
        return getInt(KEY_BOX_SESSION_STEP_2);
    }

    public int boxSessionStep3() {
        return getInt(KEY_BOX_SESSION_STEP_3);
    }

    public int boxSessionStep4() {
        return getInt(KEY_BOX_SESSION_STEP_4);
    }

    public int boxSessionStep5() {
        return getInt(KEY_BOX_SESSION_STEP_5);
    }

    public int boxSessionTailStep() {
        return getInt(KEY_BOX_SESSION_TAIL_STEP);
    }

    public int boxSessionIdleTimeoutSeconds() {
        return getInt(KEY_BOX_SESSION_IDLE_TIMEOUT_SECONDS);
    }

    public int boosterDurationSeconds() {
        return getInt(KEY_BOOSTER_DURATION_SECONDS);
    }

    public int boosterDailyLimit() {
        return getInt(KEY_BOOSTER_DAILY_LIMIT);
    }

    public int boosterLimitWindowSeconds() {
        return getInt(KEY_BOOSTER_LIMIT_WINDOW_SECONDS);
    }

    private int getInt(String key) {
        return Integer.parseInt(resolve(key).trim());
    }

    private double getDouble(String key) {
        return Double.parseDouble(resolve(key).trim());
    }

    private boolean getBoolean(String key) {
        return Boolean.parseBoolean(resolve(key).trim());
    }

    private String resolve(String key) {
        return cache.getOrDefault(key, DEFAULT_VALUES.get(key));
    }
}
