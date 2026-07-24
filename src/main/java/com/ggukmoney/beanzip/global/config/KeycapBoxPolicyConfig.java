package com.ggukmoney.beanzip.global.config;

import com.ggukmoney.beanzip.global.config.repository.AppConfigRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
public class KeycapBoxPolicyConfig {

    private static final Logger log = LoggerFactory.getLogger(KeycapBoxPolicyConfig.class);

    public static final String KEY_FREE_TICKET_REFILL_PER_HOUR = "keycapBox.freeTicket.refillPerHour";
    public static final String KEY_FREE_TICKET_CAP = "keycapBox.freeTicket.cap";
    public static final String KEY_AD_OPEN_DAILY_LIMIT = "keycapBox.adOpen.dailyLimit";
    public static final String KEY_OPEN_CYCLE_DURATION_SECONDS = "keycapBox.openCycle.durationSeconds";
    public static final String KEY_FREE_OPEN_LIMIT = "keycapBox.freeOpen.limit";
    public static final String KEY_AD_OPEN_LIMIT = "keycapBox.adOpen.limit";

    public static final Map<String, String> DEFAULT_VALUES = Map.ofEntries(
            Map.entry(KEY_FREE_TICKET_REFILL_PER_HOUR, "1"),
            Map.entry(KEY_FREE_TICKET_CAP, "8"),
            Map.entry(KEY_AD_OPEN_DAILY_LIMIT, "2"),
            Map.entry(KEY_OPEN_CYCLE_DURATION_SECONDS, "3600"),
            Map.entry(KEY_FREE_OPEN_LIMIT, "2"),
            Map.entry(KEY_AD_OPEN_LIMIT, "2")
    );

    private final AppConfigRepository appConfigRepository;

    private final Map<String, String> cache = new ConcurrentHashMap<>();

    @PostConstruct
    @Scheduled(fixedRate = 60_000)
    public void refresh() {
        Instant now = Instant.now();
        for (String key : DEFAULT_VALUES.keySet()) {
            try {
                appConfigRepository.findFirstByConfigKeyAndEffectiveAtLessThanEqualOrderByEffectiveAtDesc(key, now)
                        .map(config -> resolveValidValue(key, config.getConfigValue()))
                        .ifPresent(value -> cache.put(key, value));
            } catch (RuntimeException exception) {
                log.warn("Failed to refresh keycap box policy config from AppConfig; key={} fallback=last-known-good",
                        key,
                        exception);
            }
        }
    }

    public int refillPerHour() {
        return getInt(KEY_FREE_TICKET_REFILL_PER_HOUR);
    }

    public int cap() {
        return getInt(KEY_FREE_TICKET_CAP);
    }

    public int adOpenDailyLimit() {
        return getInt(KEY_AD_OPEN_DAILY_LIMIT);
    }

    public Duration openCycleDuration() {
        int seconds = getInt(KEY_OPEN_CYCLE_DURATION_SECONDS);
        if (seconds <= 0) {
            throw new IllegalStateException("keycapBox.openCycle.durationSeconds must be positive");
        }
        return Duration.ofSeconds(seconds);
    }

    public int freeOpenLimit() {
        return getNonNegativeInt(KEY_FREE_OPEN_LIMIT);
    }

    public int adOpenLimit() {
        return getNonNegativeInt(KEY_AD_OPEN_LIMIT);
    }

    private int getInt(String key) {
        try {
            return Integer.parseInt(resolve(key).trim());
        } catch (NumberFormatException exception) {
            throw new IllegalStateException(key + " must be an integer", exception);
        }
    }

    private int getNonNegativeInt(String key) {
        int value = getInt(key);
        if (value < 0) {
            throw new IllegalStateException(key + " must not be negative");
        }
        return value;
    }

    private String resolve(String key) {
        return cache.getOrDefault(key, DEFAULT_VALUES.get(key));
    }

    private String resolveValidValue(String key, String rawValue) {
        String fallbackValue = resolve(key);
        if (isValidPolicyValue(key, rawValue)) {
            return rawValue;
        }
        log.warn("Invalid keycap box policy config value; key={} fallback=last-known-good", key);
        return fallbackValue;
    }

    private boolean isValidPolicyValue(String key, String rawValue) {
        int value;
        try {
            value = Integer.parseInt(rawValue.trim());
        } catch (RuntimeException exception) {
            return false;
        }
        if (KEY_OPEN_CYCLE_DURATION_SECONDS.equals(key)) {
            return value > 0;
        }
        if (KEY_FREE_OPEN_LIMIT.equals(key) || KEY_AD_OPEN_LIMIT.equals(key)) {
            return value >= 0;
        }
        return true;
    }
}
