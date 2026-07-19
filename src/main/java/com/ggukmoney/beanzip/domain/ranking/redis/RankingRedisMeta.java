package com.ggukmoney.beanzip.domain.ranking.redis;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

public record RankingRedisMeta(
        String state,
        Instant lastReconciledAt,
        Instant lastSuccessfulBuildAt,
        long participantCount,
        int schemaVersion,
        Instant lastErrorAt,
        Instant lastProcessedUpdatedAt,
        long lastProcessedEntryId
) {

    public static final String STATE_BUILDING = "BUILDING";
    public static final String STATE_READY = "READY";
    public static final String STATE_FAILED = "FAILED";

    public boolean isReady() {
        return STATE_READY.equals(state);
    }

    public boolean isFresh(Instant now, java.time.Duration maxStaleness) {
        return lastReconciledAt != null && !lastReconciledAt.plus(maxStaleness).isBefore(now);
    }

    public static Optional<RankingRedisMeta> fromHash(Map<Object, Object> hash) {
        if (hash == null || hash.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new RankingRedisMeta(
                string(hash, "state"),
                instant(hash, "lastReconciledAt"),
                instant(hash, "lastSuccessfulBuildAt"),
                longValue(hash, "participantCount", 0L),
                (int) longValue(hash, "schemaVersion", 0L),
                instant(hash, "lastErrorAt"),
                instant(hash, "lastProcessedUpdatedAt"),
                longValue(hash, "lastProcessedEntryId", 0L)
        ));
    }

    private static String string(Map<Object, Object> hash, String key) {
        Object value = hash.get(key);
        return value == null ? null : value.toString();
    }

    private static Instant instant(Map<Object, Object> hash, String key) {
        String value = string(hash, key);
        return value == null || value.isBlank() ? null : Instant.parse(value);
    }

    private static long longValue(Map<Object, Object> hash, String key, long defaultValue) {
        String value = string(hash, key);
        return value == null || value.isBlank() ? defaultValue : Long.parseLong(value);
    }
}
