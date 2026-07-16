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
public class KeycapBoxPolicyConfig {

    private static final Logger log = LoggerFactory.getLogger(KeycapBoxPolicyConfig.class);

    public static final String KEY_FREE_TICKET_REFILL_PER_HOUR = "keycapBox.freeTicket.refillPerHour";
    public static final String KEY_FREE_TICKET_CAP = "keycapBox.freeTicket.cap";
    public static final String KEY_AD_OPEN_DAILY_LIMIT = "keycapBox.adOpen.dailyLimit";

    public static final Map<String, String> DEFAULT_VALUES = Map.ofEntries(
            Map.entry(KEY_FREE_TICKET_REFILL_PER_HOUR, "1"),
            Map.entry(KEY_FREE_TICKET_CAP, "8"),
            Map.entry(KEY_AD_OPEN_DAILY_LIMIT, "2")
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
            log.warn("Failed to refresh keycap box policy config from AppConfig; falling back to defaults", exception);
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

    private int getInt(String key) {
        return Integer.parseInt(resolve(key).trim());
    }

    private String resolve(String key) {
        return cache.getOrDefault(key, DEFAULT_VALUES.get(key));
    }
}
