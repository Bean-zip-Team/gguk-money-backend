package com.ggukmoney.beanzip.global.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class RankChangeNotificationPolicyConfig {

    private static final Logger log = LoggerFactory.getLogger(RankChangeNotificationPolicyConfig.class);

    public static final String KEY_MINIMUM_DIFFERENCE = "notification.rankChange.minimumDifference";

    private final AppConfigBatchLoader batchLoader;
    private volatile int minimumDifference = 1;

    @PostConstruct
    @Scheduled(fixedRate = 60_000)
    public void refresh() {
        try {
            Map<String, String> loaded = batchLoader.load(java.util.Set.of(KEY_MINIMUM_DIFFERENCE), Instant.now());
            String candidate = loaded.get(KEY_MINIMUM_DIFFERENCE);
            if (candidate == null) {
                return;
            }
            int parsed = Integer.parseInt(candidate.trim());
            if (parsed < 1) {
                log.warn("Invalid rank change notification policy value; key={} fallback=last-known-good",
                        KEY_MINIMUM_DIFFERENCE);
                return;
            }
            minimumDifference = parsed;
        } catch (RuntimeException exception) {
            log.warn("Failed to refresh rank change notification policy; fallback=last-known-good", exception);
        }
    }

    public int minimumDifference() {
        return minimumDifference;
    }
}
