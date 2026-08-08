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
import java.util.UUID;

public interface NotificationDeliveryRepository extends JpaRepository<NotificationDelivery, Long> {

    Optional<NotificationDelivery> findByDedupeKey(String dedupeKey);

    boolean existsByUserIdAndTypeAndStatusAndRequestedAtAfter(
            UUID userId,
            NotificationType type,
            NotificationDeliveryStatus status,
            Instant requestedAt
    );

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            INSERT INTO notification_delivery (
                public_id, user_id, notification_type, dedupe_key, template_set_code, context_json,
                status, requested_at, created_at, updated_at
            ) VALUES (
                :publicId, :userId, :notificationType, :dedupeKey, :templateSetCode, :contextJson,
                'PENDING', :requestedAt, :requestedAt, :requestedAt
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
}
