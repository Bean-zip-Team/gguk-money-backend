package com.ggukmoney.beanzip.global.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class CashoutPolicyConfig {

    private static final Logger log = LoggerFactory.getLogger(CashoutPolicyConfig.class);

    public static final String KEY_MINIMUM_POINT = "cashout.minimumPoint";
    public static final String KEY_POINT_TO_KRW_RATE = "cashout.pointToKrwRate";

    public static final Map<String, String> DEFAULT_VALUES = Map.ofEntries(
            Map.entry(KEY_MINIMUM_POINT, "50"),
            Map.entry(KEY_POINT_TO_KRW_RATE, "0.02")
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
                    log.warn("Invalid cashout policy config value; key={} fallback=last-known-good", key);
                }
                next.put(key, valid ? candidate : fallback);
            }
            cache = Map.copyOf(next);
        } catch (RuntimeException exception) {
            log.warn("Failed to refresh cashout policy config from AppConfig; fallback=last-known-good", exception);
        }
    }

    public int minimumPoint() {
        return getInt(KEY_MINIMUM_POINT);
    }

    public BigDecimal pointToKrwRate() {
        return getBigDecimal(KEY_POINT_TO_KRW_RATE);
    }

    private int getInt(String key) {
        return Integer.parseInt(resolve(key).trim());
    }

    private BigDecimal getBigDecimal(String key) {
        return new BigDecimal(resolve(key).trim());
    }

    private String resolve(String key) {
        return cache.getOrDefault(key, DEFAULT_VALUES.get(key));
    }

    private boolean isValidPolicyValue(String key, String rawValue) {
        try {
            if (KEY_MINIMUM_POINT.equals(key)) {
                return Integer.parseInt(rawValue.trim()) > 0;
            }
            if (KEY_POINT_TO_KRW_RATE.equals(key)) {
                return new BigDecimal(rawValue.trim()).signum() > 0;
            }
            return false;
        } catch (RuntimeException exception) {
            return false;
        }
    }
}
