package com.ggukmoney.beanzip.domain.notification.repository;

import com.ggukmoney.beanzip.domain.notification.entity.WeeklyRankingResetNotificationBatch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface WeeklyRankingResetNotificationBatchRepository
        extends JpaRepository<WeeklyRankingResetNotificationBatch, Long> {

    @Modifying
    @Query(value = """
            INSERT INTO weekly_ranking_reset_notification_batch (
                season_id, preference_cursor, enqueue_completed, completed, created_at, updated_at
            ) VALUES (:seasonId, 0, false, false, :now, :now)
            ON CONFLICT ON CONSTRAINT uq_weekly_reset_notification_batch_season DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(@Param("seasonId") Long seasonId, @Param("now") Instant now);

    Optional<WeeklyRankingResetNotificationBatch> findFirstByEnqueueCompletedFalseAndCompletedFalseOrderByIdAsc();

    Optional<WeeklyRankingResetNotificationBatch> findFirstByEnqueueCompletedTrueAndCompletedFalseOrderByIdAsc();

    Optional<WeeklyRankingResetNotificationBatch> findBySeasonId(Long seasonId);
}
