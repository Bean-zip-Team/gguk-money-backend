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
public class CashoutPolicyConfig {

    private static final Logger log = LoggerFactory.getLogger(CashoutPolicyConfig.class);

    public static final String KEY_MINIMUM_POINT = "cashout.minimumPoint";
    public static final String KEY_POINT_TO_KRW_RATE = "cashout.pointToKrwRate";

    public static final Map<String, String> DEFAULT_VALUES = Map.ofEntries(
            Map.entry(KEY_MINIMUM_POINT, "10"),
            Map.entry(KEY_POINT_TO_KRW_RATE, "0.7")
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
            log.warn("Failed to refresh cashout policy config from AppConfig; falling back to defaults", exception);
        }
    }

    public int minimumPoint() {
        return getInt(KEY_MINIMUM_POINT);
    }

    public double pointToKrwRate() {
        return getDouble(KEY_POINT_TO_KRW_RATE);
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
