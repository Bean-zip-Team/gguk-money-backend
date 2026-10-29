package com.ggukmoney.beanzip.domain.tap.repository;

import com.ggukmoney.beanzip.domain.tap.entity.TapBatch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TapBatchRepository extends JpaRepository<TapBatch, Long> {

    Optional<TapBatch> findByUserIdAndTapSessionIdAndSequence(UUID userId, UUID tapSessionId, Long sequence);

    /**
     * 어뷰징 탐지용으로 [windowStart, windowEnd) 창 안의 탭 배치를 ACTIVE 유저별로 요약한다.
     *
     * <p>실제 탭은 accepted_count 를 그 시각 부스터 배율로 나눈 값이다. 나누는 쪽이 탭 수를 적게 잡아
     * 오탐이 줄어든다. 배치 사이가 60초 넘게 끊기면 새 구간으로 본다. cv 는 구간 안의 탭당 ms 변동계수이고,
     * 10탭 이상 배치가 10개 이상인 구간만 계산한다. 정지·탈퇴 유저는 이미 조치된 상태라 뺀다. 정지 이력(suspended_at)이 남은 유저도 정지로 본다.
     *
     * <p>ponytail: tap_batch 에 created_at 인덱스가 없어 창 조건이 전체를 훑는다. 2026-10 기준 6만 행이라
     * 문제없고, 수백만 행이 되면 (created_at) 인덱스를 추가한다.
     */
    @Query(value = """
            WITH b AS (
                SELECT tb.user_id, tb.created_at, tb.accepted_count,
                       tb.accepted_count / COALESCE((
                           SELECT MAX(bg.multiplier) FROM booster_grant bg
                           WHERE bg.user_id = tb.user_id
                             AND tb.created_at >= bg.starts_at
                             AND tb.created_at < bg.expires_at + INTERVAL '10 seconds'), 1) AS physical,
                       EXTRACT(EPOCH FROM tb.created_at - LAG(tb.created_at)
                           OVER (PARTITION BY tb.user_id ORDER BY tb.created_at)) AS gap
                FROM tap_batch tb
                JOIN app_user u ON u.id = tb.user_id AND u.status = 'ACTIVE' AND u.suspended_at IS NULL
                WHERE tb.created_at >= :windowStart AND tb.created_at < :windowEnd AND tb.accepted_count > 0
            ),
            bb AS (
                SELECT b.*, SUM(CASE WHEN b.gap IS NULL OR b.gap > 60 THEN 1 ELSE 0 END)
                           OVER (PARTITION BY b.user_id ORDER BY b.created_at) AS burst_id
                FROM b
            ),
            minutes AS (
                SELECT user_id, SUM(physical) AS taps, COUNT(*) AS batches,
                       EXTRACT(EPOCH FROM MAX(created_at) - MIN(created_at)) AS span_seconds
                FROM bb
                GROUP BY user_id, date_trunc('minute', created_at)
            ),
            bursts AS (
                SELECT user_id,
                       EXTRACT(EPOCH FROM MAX(created_at) - MIN(created_at)) / 60 AS minutes,
                       CASE WHEN COUNT(*) FILTER (WHERE accepted_count >= 10 AND gap BETWEEN 0.2 AND 60) >= 10
                            THEN STDDEV_SAMP(gap * 1000 / physical) FILTER (WHERE accepted_count >= 10 AND gap BETWEEN 0.2 AND 60)
                                 / NULLIF(AVG(gap * 1000 / physical) FILTER (WHERE accepted_count >= 10 AND gap BETWEEN 0.2 AND 60), 0)
                       END AS cv
                FROM bb
                GROUP BY user_id, burst_id
            )
            SELECT m.user_id AS userId,
                   m.physical_taps AS physicalTaps,
                   m.max_minute_taps AS maxMinuteTaps,
                   m.fast_minutes AS fastMinutes,
                   s.min_cv AS minCv,
                   s.longest_minutes AS longestBurstMinutes,
                   s.longest_regular_minutes AS longestRegularBurstMinutes
            FROM (
                SELECT user_id,
                       CAST(ROUND(SUM(taps)) AS bigint) AS physical_taps,
                       CAST(ROUND(MAX(taps)) AS bigint) AS max_minute_taps,
                       COUNT(*) FILTER (WHERE taps > :fastMinuteTaps
                                          AND batches >= :fastMinuteMinBatches
                                          AND span_seconds >= :fastMinuteMinSpanSeconds) AS fast_minutes
                FROM minutes
                GROUP BY user_id
            ) m
            JOIN (
                SELECT user_id,
                       CAST(MIN(cv) AS double precision) AS min_cv,
                       CAST(MAX(minutes) AS double precision) AS longest_minutes,
                       CAST(MAX(minutes) FILTER (WHERE cv < :regularCv) AS double precision) AS longest_regular_minutes
                FROM bursts
                GROUP BY user_id
            ) s ON s.user_id = m.user_id
            ORDER BY m.user_id
            """, nativeQuery = true)
    List<AbuseStatsProjection> findAbuseStats(
            @Param("windowStart") Instant windowStart,
            @Param("windowEnd") Instant windowEnd,
            @Param("fastMinuteTaps") long fastMinuteTaps,
            @Param("fastMinuteMinBatches") int fastMinuteMinBatches,
            @Param("fastMinuteMinSpanSeconds") int fastMinuteMinSpanSeconds,
            @Param("regularCv") double regularCv
    );

    interface AbuseStatsProjection {
        UUID getUserId();

        Long getPhysicalTaps();

        Long getMaxMinuteTaps();

        Long getFastMinutes();

        /** 계산할 만큼 배치가 쌓인 구간이 없으면 null. */
        Double getMinCv();

        Double getLongestBurstMinutes();

        /** cv 가 기준 미만인 구간이 없으면 null. */
        Double getLongestRegularBurstMinutes();
    }
}
