package com.ggukmoney.beanzip.domain.ranking.boost;

import com.ggukmoney.beanzip.global.config.AppConfigBatchLoader;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.*;

/** One coherent, uncached policy; deliberately not registered in TapConfigSeeder. */
@Slf4j
@Component
@RequiredArgsConstructor
public class SystemRankingBoostPolicy {
    public static final String KEY = "ranking.systemBoost.policy";
    private final AppConfigBatchLoader loader;
    private final ObjectMapper mapper;

    public Optional<Snapshot> load(Instant now) {
        try {
            String raw = loader.load(Set.of(KEY), now).get(KEY);
            if (raw == null) return Optional.empty();
            return Optional.of(parse(mapper, raw));
        } catch (RuntimeException exception) {
            log.error("System ranking boost policy unavailable; execution disabled", exception);
            return Optional.empty();
        }
    }

    /** 운영 화면(/ops/config)도 저장 전에 이 규칙으로 검사한다. 잘못되면 예외를 던진다. */
    public static Snapshot parse(ObjectMapper mapper, String raw) {
        var tree = mapper.readTree(raw);
        if (!tree.isObject() || !tree.path("enabled").isBoolean() || !tree.path("internalUserIds").isArray()
                || !tree.path("minimumLeaderScore").isIntegralNumber()
                || !tree.path("minIncrement").isIntegralNumber() || !tree.path("maxIncrement").isIntegralNumber()) {
            throw new IllegalArgumentException("incomplete or incorrectly typed boost policy");
        }
        return mapper.readValue(raw, Snapshot.class);
    }

    public String serialize(Snapshot snapshot) {
        return mapper.writeValueAsString(snapshot);
    }

    public record Snapshot(boolean enabled, Set<UUID> internalUserIds, long minimumLeaderScore,
                           int minIncrement, int maxIncrement) {
        public Snapshot {
            internalUserIds = Set.copyOf(Objects.requireNonNull(internalUserIds));
            if (minimumLeaderScore < 0 || minIncrement <= 0 || maxIncrement < minIncrement) {
                throw new IllegalArgumentException("invalid boost thresholds");
            }
        }
    }
}
