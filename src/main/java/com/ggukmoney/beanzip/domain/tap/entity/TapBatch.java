package com.ggukmoney.beanzip.domain.tap.entity;

import com.ggukmoney.beanzip.domain.user.entity.AppUser;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Getter
@Entity
@Table(
        name = "tap_batch",
        uniqueConstraints = @UniqueConstraint(name = "ux_tap_batch_session_sequence", columnNames = {"user_id", "tap_session_id", "sequence"})
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TapBatch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true)
    private UUID publicId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @Column(name = "tap_session_id", nullable = false)
    private UUID tapSessionId;

    @Column(name = "sequence", nullable = false)
    private Long sequence;

    @Column(name = "submitted_count", nullable = false)
    private Integer submittedCount;

    @Column(name = "accepted_count", nullable = false)
    private Integer acceptedCount = 0;

    @Column(name = "rejected_count", nullable = false)
    private Integer rejectedCount = 0;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "ended_at", nullable = false)
    private Instant endedAt;

    @Column(name = "elapsed_ms", nullable = false)
    private Integer elapsedMs;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "interval_stats", columnDefinition = "jsonb")
    private String intervalStats;

    @Enumerated(EnumType.STRING)
    @Column(name = "risk_level", nullable = false, length = 20)
    private RiskLevel riskLevel = RiskLevel.NORMAL;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private Status status = Status.RECEIVED;

    @Column(name = "request_hash", nullable = false, length = 255)
    private String requestHash;

    @Column(name = "processed_at")
    private Instant processedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

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

    public enum RiskLevel {
        NORMAL,
        SUSPICIOUS,
        BLOCKED
    }

    public enum Status {
        RECEIVED,
        ACCEPTED,
        PARTIAL,
        REJECTED
    }
}
