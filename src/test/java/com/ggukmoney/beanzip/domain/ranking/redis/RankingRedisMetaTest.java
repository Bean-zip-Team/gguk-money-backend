package com.ggukmoney.beanzip.domain.ranking.redis;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RankingRedisMetaTest {

    @Test
    void acceptsBoundedStalenessWithinMaxStaleness() {
        RankingRedisMeta meta = readyMeta(Instant.parse("2026-07-19T00:00:00Z"));

        assertThat(meta.isFresh(
                Instant.parse("2026-07-19T00:01:59Z"),
                Duration.ofSeconds(120)
        )).isTrue();
    }

    @Test
    void rejectsRedisProjectionAfterMaxStaleness() {
        RankingRedisMeta meta = readyMeta(Instant.parse("2026-07-19T00:00:00Z"));

        assertThat(meta.isFresh(
                Instant.parse("2026-07-19T00:02:01Z"),
                Duration.ofSeconds(120)
        )).isFalse();
    }

    @Test
    void parsesCompositeWatermarkFromHash() {
        RankingRedisMeta meta = RankingRedisMeta.fromHash(Map.of(
                "state", "READY",
                "lastReconciledAt", "2026-07-19T00:01:00Z",
                "lastSuccessfulBuildAt", "2026-07-19T00:00:00Z",
                "participantCount", "3",
                "schemaVersion", "1",
                "lastProcessedUpdatedAt", "2026-07-19T00:00:30Z",
                "lastProcessedEntryId", "42"
        )).orElseThrow();

        assertThat(meta.lastProcessedUpdatedAt()).isEqualTo(Instant.parse("2026-07-19T00:00:30Z"));
        assertThat(meta.lastProcessedEntryId()).isEqualTo(42L);
    }

    private RankingRedisMeta readyMeta(Instant lastReconciledAt) {
        return new RankingRedisMeta(
                RankingRedisMeta.STATE_READY,
                lastReconciledAt,
                null,
                1,
                1,
                null,
                lastReconciledAt,
                1
        );
    }
}
