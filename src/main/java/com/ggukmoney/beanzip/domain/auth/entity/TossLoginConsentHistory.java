package com.ggukmoney.beanzip.domain.auth.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Getter
@Entity
@Table(name = "toss_login_consent_history")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TossLoginConsentHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "term_tag", nullable = false, length = 255)
    private String termTag;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private Status status;

    @Column(name = "event_source", nullable = false, length = 30)
    private String eventSource;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public static TossLoginConsentHistory agreed(UUID userId, String termTag, String eventSource, Instant occurredAt) {
        return create(userId, termTag, Status.AGREED, eventSource, occurredAt);
    }

    public static TossLoginConsentHistory withdrawn(UUID userId, String termTag, String eventSource, Instant occurredAt) {
        return create(userId, termTag, Status.WITHDRAWN, eventSource, occurredAt);
    }

    private static TossLoginConsentHistory create(UUID userId, String termTag, Status status, String eventSource, Instant occurredAt) {
        TossLoginConsentHistory history = new TossLoginConsentHistory();
        history.userId = userId;
        history.termTag = termTag;
        history.status = status;
        history.eventSource = eventSource;
        history.occurredAt = occurredAt;
        return history;
    }

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public enum Status {
        AGREED,
        WITHDRAWN
    }
}
