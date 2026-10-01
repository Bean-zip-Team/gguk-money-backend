package com.ggukmoney.beanzip.domain.notification.repository;

import com.ggukmoney.beanzip.domain.notification.entity.NotificationDelivery;
import com.ggukmoney.beanzip.domain.notification.entity.NotificationDeliveryStatus;
import com.ggukmoney.beanzip.domain.notification.entity.NotificationType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface NotificationDeliveryRepository extends JpaRepository<NotificationDelivery, Long> {

    Optional<NotificationDelivery> findByDedupeKey(String dedupeKey);

    boolean existsByUserIdAndTypeAndStatusAndRequestedAtAfter(
            UUID userId,
            NotificationType type,
            NotificationDeliveryStatus status,
            Instant requestedAt
    );

    @Query("""
            SELECT DISTINCT delivery.userId
            FROM NotificationDelivery delivery
            WHERE delivery.userId IN :userIds
              AND delivery.type = :type
              AND delivery.status = :status
              AND delivery.requestedAt > :requestedAt
            """)
    List<UUID> findUserIdsWithRecentDelivery(
            @Param("userIds") Collection<UUID> userIds,
            @Param("type") NotificationType type,
            @Param("status") NotificationDeliveryStatus status,
            @Param("requestedAt") Instant requestedAt
    );

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            INSERT INTO notification_delivery (
                public_id, user_id, notification_type, dedupe_key, template_set_code, context_json,
                attempt_count, status, requested_at, created_at, updated_at
            ) VALUES (
                :publicId, :userId, :notificationType, :dedupeKey, :templateSetCode, :contextJson,
                0, 'PENDING', :requestedAt, :requestedAt, :requestedAt
            )
            ON CONFLICT ON CONSTRAINT uq_notification_delivery_dedupe_key DO NOTHING
            """, nativeQuery = true)
    int insertPendingIfAbsent(
            @Param("publicId") UUID publicId,
            @Param("userId") UUID userId,
            @Param("notificationType") String notificationType,
            @Param("dedupeKey") String dedupeKey,
            @Param("templateSetCode") String templateSetCode,
            @Param("contextJson") String contextJson,
            @Param("requestedAt") Instant requestedAt
    );

    default int insertPendingIfAbsent(
            UUID publicId,
            UUID userId,
            String notificationType,
            String dedupeKey,
            String templateSetCode,
            Instant requestedAt
    ) {
        return insertPendingIfAbsent(publicId, userId, notificationType, dedupeKey, templateSetCode, "{}", requestedAt);
    }

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            INSERT INTO notification_delivery (
                public_id, user_id, notification_type, dedupe_key, template_set_code, context_json,
                weekly_reset_batch_id, attempt_count, status, requested_at, created_at, updated_at
            )
            SELECT :publicId, :userId, 'RANK_CHANGE', :dedupeKey, :templateSetCode, '{}',
                   :batchId, 0, 'PENDING', :now, :now, :now
            WHERE EXISTS (
                SELECT 1 FROM notification_preference preference
                WHERE preference.user_id = :userId
                  AND preference.notification_type = 'RANK_CHANGE'
                  AND preference.enabled = true
                  AND preference.agreement_status = 'AGREED'
            )
              AND EXISTS (
                SELECT 1
                FROM ranking_entry entry
                JOIN ranking_season season ON season.id = entry.season_id
                JOIN app_user user_record ON user_record.id = entry.user_id
                WHERE entry.season_id = :seasonId
                  AND entry.user_id = :userId
                  AND entry.final_rank IS NOT NULL
                  AND entry.finalized_at IS NOT NULL
                  AND season.ranking_type = 'WEEKLY'
                  AND season.status = 'CLOSED'
                  AND user_record.status = 'ACTIVE'
            )
            ON CONFLICT ON CONSTRAINT uq_notification_delivery_dedupe_key DO NOTHING
            """, nativeQuery = true)
    int insertWeeklyResetPendingIfAbsent(
            @Param("publicId") UUID publicId,
            @Param("userId") UUID userId,
            @Param("batchId") Long batchId,
            @Param("seasonId") Long seasonId,
            @Param("dedupeKey") String dedupeKey,
            @Param("templateSetCode") String templateSetCode,
            @Param("now") Instant now
    );

    @Query("""
            SELECT delivery
            FROM NotificationDelivery delivery
            WHERE delivery.weeklyResetBatchId IS NOT NULL
              AND ((delivery.status = com.ggukmoney.beanzip.domain.notification.entity.NotificationDeliveryStatus.PENDING
                    AND (delivery.nextAttemptAt IS NULL OR delivery.nextAttemptAt <= :now))
                OR (delivery.status = com.ggukmoney.beanzip.domain.notification.entity.NotificationDeliveryStatus.RETRY_WAITING
                    AND delivery.nextAttemptAt <= :now))
            ORDER BY delivery.id ASC
            """)
    List<NotificationDelivery> findDueWeeklyResetDeliveries(@Param("now") Instant now, org.springframework.data.domain.Pageable pageable);

    @Query("""
            SELECT COUNT(delivery)
            FROM NotificationDelivery delivery
            WHERE delivery.weeklyResetBatchId IS NOT NULL
              AND delivery.lastAttemptAt >= :since
            """)
    long countWeeklyResetAttemptsSince(@Param("since") Instant since);

    boolean existsByWeeklyResetBatchIdAndStatusIn(Long weeklyResetBatchId, Collection<NotificationDeliveryStatus> statuses);
}
