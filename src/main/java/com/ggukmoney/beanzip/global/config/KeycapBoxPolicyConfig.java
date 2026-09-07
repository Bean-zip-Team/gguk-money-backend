package com.ggukmoney.beanzip.global.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class KeycapBoxPolicyConfig {

    private static final Logger log = LoggerFactory.getLogger(KeycapBoxPolicyConfig.class);

    public static final String KEY_OPEN_CYCLE_DURATION_SECONDS = "keycapBox.openCycle.durationSeconds";
    public static final String KEY_FREE_OPEN_LIMIT = "keycapBox.freeOpen.limit";
    public static final String KEY_AD_OPEN_LIMIT = "keycapBox.adOpen.limit";
    public static final String KEY_BULK_OPEN_LIMIT = "keycapBox.bulkOpen.limit";

    public static final Map<String, String> DEFAULT_VALUES = Map.ofEntries(
            Map.entry(KEY_OPEN_CYCLE_DURATION_SECONDS, "3600"),
            Map.entry(KEY_FREE_OPEN_LIMIT, "2"),
            Map.entry(KEY_AD_OPEN_LIMIT, "6"),
            Map.entry(KEY_BULK_OPEN_LIMIT, "30")
    );

    private final AppConfigBatchLoader batchLoader;

    private volatile Map<String, String> cache = Map.copyOf(DEFAULT_VALUES);

    @PostConstruct
    @Scheduled(fixedRate = 60_000)
    public void refresh() {
        try {
            Map<String, String> loaded = batchLoader.load(DEFAULT_VALUES.keySet(), Instant.now());
            Map<String, String> current = cache;
            Map<String, String> next = new HashMap<>(DEFAULT_VALUES.size());
            for (String key : DEFAULT_VALUES.keySet()) {
                String fallback = current.getOrDefault(key, DEFAULT_VALUES.get(key));
                String candidate = loaded.get(key);
                boolean valid = candidate != null && isValidPolicyValue(key, candidate);
                if (candidate != null && !valid) {
                    log.warn("Invalid keycap box policy config value; key={} fallback=last-known-good", key);
                }
                next.put(key, valid ? candidate : fallback);
            }
            cache = Map.copyOf(next);
        } catch (RuntimeException exception) {
            log.warn("Failed to refresh keycap box policy config from AppConfig; fallback=last-known-good", exception);
        }
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

    /**
     * 일괄 개봉 상한 (BEA-280). 예산 방어값이 아니라 체감·연출 기준으로 고른 값이다.
     *
     * <p>전량을 열면 재고가 0 이 되어 이후 광고 개봉 동기까지 사라진다. 30 개를 열고 나머지를
     * 남기면 루프가 계속 돈다. 활동 유저 보유 중앙값이 32 개라 대부분은 거의 다 열린다.
     */
    public int bulkOpenLimit() {
        return getInt(KEY_BULK_OPEN_LIMIT);
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
        return false;
    }
}
