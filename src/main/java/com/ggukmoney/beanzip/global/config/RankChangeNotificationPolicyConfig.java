package com.ggukmoney.beanzip.global.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class RankChangeNotificationPolicyConfig {

    private static final Logger log = LoggerFactory.getLogger(RankChangeNotificationPolicyConfig.class);

    public static final String KEY_MINIMUM_DIFFERENCE = "notification.rankChange.minimumDifference";
    /** 같은 유저에게 랭킹 변동 알림을 다시 보내기까지 기다리는 분. 0 이면 쿨다운 없음. */
    public static final String KEY_COOLDOWN_MINUTES = "notification.rankChange.cooldownMinutes";
    public static final Set<String> KEYS = Set.of(KEY_MINIMUM_DIFFERENCE, KEY_COOLDOWN_MINUTES);

    private final AppConfigBatchLoader batchLoader;
    private volatile int minimumDifference = 1;
    // 설정 파일(app.smart-message.rank-change.cooldown)로 운영하던 값이다. 행이 없으면 그대로 간다.
    private volatile Duration cooldown = Duration.ofHours(3);

    @PostConstruct
    @Scheduled(fixedRate = 60_000)
    public void refresh() {
        Map<String, String> loaded;
        try {
            loaded = batchLoader.load(KEYS, Instant.now());
        } catch (RuntimeException exception) {
            log.warn("Failed to refresh rank change notification policy; fallback=last-known-good", exception);
            return;
        }
        Integer difference = parse(loaded, KEY_MINIMUM_DIFFERENCE, 1);
        if (difference != null) {
            minimumDifference = difference;
        }
        Integer cooldownMinutes = parse(loaded, KEY_COOLDOWN_MINUTES, 0);
        if (cooldownMinutes != null) {
            cooldown = Duration.ofMinutes(cooldownMinutes);
        }
    }

    /** 값이 없거나 잘못됐으면 null 이다. 호출자는 마지막으로 읽은 값을 유지한다. */
    private Integer parse(Map<String, String> loaded, String key, int min) {
        String candidate = loaded.get(key);
        if (candidate == null) {
            return null;
        }
        try {
            int parsed = Integer.parseInt(candidate.trim());
            if (parsed >= min) {
                return parsed;
            }
        } catch (NumberFormatException ignored) {
            // 아래에서 경고한다.
        }
        log.warn("Invalid rank change notification policy value; key={} fallback=last-known-good", key);
        return null;
    }

    public int minimumDifference() {
        return minimumDifference;
    }

    public Duration cooldown() {
        return cooldown;
    }
}
