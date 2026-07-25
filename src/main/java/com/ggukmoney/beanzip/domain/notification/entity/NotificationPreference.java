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
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.UUID;

@Getter
@Entity
@Table(
        name = "notification_preference",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_notification_preference_user_type",
                columnNames = {"user_id", "notification_type"}
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NotificationPreference {

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

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    @Enumerated(EnumType.STRING)
    @Column(name = "agreement_status", nullable = false, length = 30)
    private NotificationAgreementStatus agreementStatus = NotificationAgreementStatus.NOT_REQUESTED;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static NotificationPreference defaultOf(UUID userId, NotificationType type) {
        if (userId == null || type == null) {
            throw new IllegalArgumentException("userId and type are required");
        }
        NotificationPreference preference = new NotificationPreference();
        preference.userId = userId;
        preference.type = type;
        preference.enabled = false;
        preference.agreementStatus = NotificationAgreementStatus.NOT_REQUESTED;
        return preference;
    }

    public void applyAgreement(String agreementResult) {
        String normalized = StringUtils.hasText(agreementResult) ? agreementResult.trim() : "";
        if ("newAgreement".equals(normalized) || "alreadyAgreed".equals(normalized)) {
            this.agreementStatus = NotificationAgreementStatus.AGREED;
            this.enabled = true;
            return;
        }
        if ("agreementRejected".equals(normalized)) {
            this.agreementStatus = NotificationAgreementStatus.REJECTED;
            this.enabled = false;
            return;
        }
        throw new IllegalArgumentException("unsupported agreementResult: " + agreementResult);
    }

    public boolean isSendable() {
        return enabled && agreementStatus == NotificationAgreementStatus.AGREED;
    }

    public void updateEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        if (publicId == null) {
            publicId = UUID.randomUUID();
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
