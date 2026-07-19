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

    public static Optional<RankingRedisMeta> fromHash(Map<String, String> hash) {
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

    private static String string(Map<String, String> hash, String key) {
        return hash.get(key);
    }

    private static Instant instant(Map<String, String> hash, String key) {
        String value = string(hash, key);
        return value == null || value.isBlank() ? null : Instant.parse(value);
    }

    private static long longValue(Map<String, String> hash, String key, long defaultValue) {
        String value = string(hash, key);
        return value == null || value.isBlank() ? defaultValue : Long.parseLong(value);
    }
}
