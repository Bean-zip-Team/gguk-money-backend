package com.ggukmoney.beanzip.domain.notification.repository;

import com.ggukmoney.beanzip.domain.notification.entity.NotificationDelivery;
import com.ggukmoney.beanzip.domain.notification.entity.NotificationDeliveryStatus;
import com.ggukmoney.beanzip.domain.notification.entity.NotificationType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

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

    /**
     * 친구 푸시를 발송 대기로 넣는다 (BEA-390).
     *
     * <p>동의는 보내는 타입이 아니라 FRIEND 로 본다. 같은 키의 행이 이미 있으면 넣지 않는다. 키에 받는
     * 사람·종류·보낼 날짜가 들어 있어서 한 사람에게 종류별로 하루 한 통만 나간다.
     *
     * <p>호출한 쪽의 트랜잭션 안에서 실행되므로 영속성 컨텍스트를 비우지 않는다. 비우면 호출한 쪽이
     * 들고 있던 엔티티가 분리되어 이후 변경이 저장되지 않는다.
     */
    @Modifying(flushAutomatically = true)
    @Query(value = """
            INSERT INTO notification_delivery (
                public_id, user_id, notification_type, dedupe_key, template_set_code, context_json,
                attempt_count, status, requested_at, next_attempt_at, created_at, updated_at
            )
            SELECT :publicId, :userId, :notificationType, :dedupeKey, :templateSetCode, '{}',
                   0, 'PENDING', :now, :sendAt, :now, :now
            WHERE EXISTS (
                SELECT 1 FROM notification_preference preference
                WHERE preference.user_id = :userId
                  AND preference.notification_type = 'FRIEND'
                  AND preference.enabled = true
                  AND preference.agreement_status = 'AGREED'
            )
            ON CONFLICT (dedupe_key) DO NOTHING
            """, nativeQuery = true)
    int insertFriendPendingIfAbsent(
            @Param("publicId") UUID publicId,
            @Param("userId") UUID userId,
            @Param("notificationType") String notificationType,
            @Param("dedupeKey") String dedupeKey,
            @Param("templateSetCode") String templateSetCode,
            @Param("now") Instant now,
            @Param("sendAt") Instant sendAt
    );

    /**
     * 보낼 시각이 된 친구 푸시와, 보내도 되는지 판단할 값을 함께 읽는다.
     *
     * <p>넣을 때 동의를 확인했어도 아침 8시까지 기다리는 사이에 동의를 끄거나 탈퇴할 수 있어서 보내기
     * 직전에 다시 본다.
     */
    @Query(value = """
            SELECT delivery.id AS id,
                   delivery.user_id AS userId,
                   delivery.template_set_code AS templateSetCode,
                   delivery.attempt_count AS attemptCount,
                   (delivery.attempt_count = 0 AND delivery.next_attempt_at < :staleBefore) AS expired,
                   (preference.enabled AND preference.agreement_status = 'AGREED') AS consented,
                   user_record.status::text AS userStatus
            FROM notification_delivery delivery
            LEFT JOIN notification_preference preference
                   ON preference.user_id = delivery.user_id
                  AND preference.notification_type = 'FRIEND'
            LEFT JOIN app_user user_record ON user_record.id = delivery.user_id
            WHERE delivery.notification_type IN ('FRIEND_CHEER', 'FRIEND_INVITE_SUCCESS')
              AND delivery.status IN ('PENDING', 'RETRY_WAITING')
              AND delivery.next_attempt_at <= :now
            ORDER BY delivery.next_attempt_at, delivery.id
            LIMIT :pageSize
            """, nativeQuery = true)
    List<DueFriendDelivery> findDueFriendDeliveries(
            @Param("now") Instant now,
            @Param("staleBefore") Instant staleBefore,
            @Param("pageSize") int pageSize
    );

    /**
     * 발송을 한 번 시도한다고 표시한다. 보내는 중에 서버가 죽어도 {@code leaseUntil} 전에는 다시 집지 않는다.
     *
     * @return 표시했으면 1, 다른 실행이 먼저 집었거나 시도 횟수를 다 썼으면 0
     */
    @Transactional
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            UPDATE notification_delivery
            SET attempt_count = attempt_count + 1,
                last_attempt_at = :now,
                next_attempt_at = :leaseUntil,
                status = 'PENDING',
                updated_at = :now
            WHERE id = :id
              AND status IN ('PENDING', 'RETRY_WAITING')
              AND next_attempt_at <= :now
              AND attempt_count < :maxAttempts
            """, nativeQuery = true)
    int claimFriendAttempt(
            @Param("id") Long id,
            @Param("now") Instant now,
            @Param("leaseUntil") Instant leaseUntil,
            @Param("maxAttempts") int maxAttempts
    );

    interface DueFriendDelivery {
        Long getId();

        UUID getUserId();

        String getTemplateSetCode();

        Integer getAttemptCount();

        Boolean getExpired();

        /** 동의 행이 없으면 null 이다. */
        Boolean getConsented();

        /** 사용자 행이 없으면 null 이다. */
        String getUserStatus();
    }
}
