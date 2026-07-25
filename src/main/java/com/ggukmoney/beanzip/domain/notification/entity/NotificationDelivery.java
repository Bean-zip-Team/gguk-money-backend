package com.ggukmoney.beanzip.domain.notification.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Getter
@Entity
@Table(
        name = "notification_delivery",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_notification_delivery_dedupe_key",
                columnNames = "dedupe_key"
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NotificationDelivery {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true)
    private UUID publicId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "notification_type", nullable = false, length = 60)
    private NotificationType type;

    @Column(name = "dedupe_key", nullable = false, length = 255)
    private String dedupeKey;

    @Column(name = "template_set_code", nullable = false, length = 120)
    private String templateSetCode;

    @Column(name = "context_json", nullable = false, columnDefinition = "text")
    private String contextJson;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private NotificationDeliveryStatus status;

    @Column(name = "content_id", length = 255)
    private String contentId;

    @Column(name = "failure_code", length = 120)
    private String failureCode;

    @Column(name = "failure_reason", columnDefinition = "text")
    private String failureReason;

    @Column(name = "provider_response_json", columnDefinition = "text")
    private String providerResponseJson;

    @Column(name = "requested_at", nullable = false)
    private Instant requestedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static NotificationDelivery pending(
            UUID userId,
            NotificationType type,
            String dedupeKey,
            String templateSetCode,
            String contextJson,
            Instant requestedAt
    ) {
        NotificationDelivery delivery = new NotificationDelivery();
        delivery.userId = userId;
        delivery.type = type;
        delivery.dedupeKey = dedupeKey;
        delivery.templateSetCode = templateSetCode;
        delivery.contextJson = contextJson;
        delivery.requestedAt = requestedAt;
        return delivery;
    }

    public static NotificationDelivery pending(UUID userId, NotificationType type, String dedupeKey, String templateSetCode, Instant requestedAt) {
        return pending(userId, type, dedupeKey, templateSetCode, "{}", requestedAt);
    }

    public void markSent(String contentId, String providerResponseJson) {
        this.status = NotificationDeliveryStatus.SENT;
        this.contentId = contentId;
        this.failureCode = null;
        this.failureReason = null;
        this.providerResponseJson = providerResponseJson;
    }

    public void markFailed(String failureCode, String failureReason, String providerResponseJson) {
        this.status = NotificationDeliveryStatus.FAILED;
        this.failureCode = failureCode;
        this.failureReason = failureReason;
        this.providerResponseJson = providerResponseJson;
    }

    public void markRetryWaiting(String failureCode, String failureReason, String providerResponseJson) {
        this.status = NotificationDeliveryStatus.RETRY_WAITING;
        this.failureCode = failureCode;
        this.failureReason = failureReason;
        this.providerResponseJson = providerResponseJson;
    }

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        if (publicId == null) {
            publicId = UUID.randomUUID();
        }
        if (requestedAt == null) {
            requestedAt = now;
        }
        if (status == null) {
            status = NotificationDeliveryStatus.PENDING;
        }
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }
}
