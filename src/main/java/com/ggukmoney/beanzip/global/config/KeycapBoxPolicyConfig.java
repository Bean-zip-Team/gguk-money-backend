package com.ggukmoney.beanzip.global.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * 뽑기 정책 (BEA-329). 상자 개봉 주기·무료/광고/일괄 개봉 한도는 개봉과 함께 사라졌다.
 */
@Component
@RequiredArgsConstructor
public class KeycapBoxPolicyConfig {

    private static final Logger log = LoggerFactory.getLogger(KeycapBoxPolicyConfig.class);

    /** 뽑기 1회에 드는 조각 수. 5 = 280탭 = 드롭 곡선 앞 구간 전체. */
    public static final String KEY_DRAW_PRICE = "keycap.draw.price";

    public static final Map<String, String> DEFAULT_VALUES = Map.of(KEY_DRAW_PRICE, "5");

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
                boolean valid = candidate != null && isValidPolicyValue(candidate);
                if (candidate != null && !valid) {
                    log.warn("Invalid keycap draw policy config value; key={} fallback=last-known-good", key);
                }
                next.put(key, valid ? candidate : fallback);
            }
            cache = Map.copyOf(next);
        } catch (RuntimeException exception) {
            log.warn("Failed to refresh keycap draw policy config from AppConfig; fallback=last-known-good", exception);
        }
    }

    public int drawPrice() {
        int price;
        try {
            price = Integer.parseInt(cache.getOrDefault(KEY_DRAW_PRICE, DEFAULT_VALUES.get(KEY_DRAW_PRICE)).trim());
        } catch (NumberFormatException exception) {
            throw new IllegalStateException(KEY_DRAW_PRICE + " must be an integer", exception);
        }
        if (price <= 0) {
            throw new IllegalStateException(KEY_DRAW_PRICE + " must be positive");
        }
        return price;
    }

    private boolean isValidPolicyValue(String rawValue) {
        try {
            return Integer.parseInt(rawValue.trim()) > 0;
        } catch (RuntimeException exception) {
            return false;
        }
    }
}
