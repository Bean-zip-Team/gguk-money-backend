package com.ggukmoney.beanzip.domain.tap.entity;

import com.ggukmoney.beanzip.domain.user.entity.AppUser;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Getter
@Entity
@Table(name = "user_tap_session")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserTapSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true)
    private UUID publicId;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private AppUser user;

    @Column(name = "session_started_at", nullable = false)
    private Instant sessionStartedAt;

    @Column(name = "session_expires_at", nullable = false)
    private Instant sessionExpiresAt;

    @Column(name = "last_activity_at", nullable = false)
    private Instant lastActivityAt;

    @Column(name = "session_valid_tap_count", nullable = false)
    private Long sessionValidTapCount = 0L;

    @Column(name = "boxes_dropped_in_session", nullable = false)
    private Integer boxesDroppedInSession = 0;

    @Column(name = "next_box_target", nullable = false)
    private Integer nextBoxTarget;

    @Version
    @Column(name = "version", nullable = false)
    private Long version = 0L;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static UserTapSession createFor(AppUser user, Instant startedAt, Instant expiresAt, int initialBoxTarget) {
        UserTapSession session = new UserTapSession();
        session.user = user;
        session.sessionStartedAt = startedAt;
        session.sessionExpiresAt = expiresAt;
        session.lastActivityAt = startedAt;
        session.nextBoxTarget = initialBoxTarget;
        return session;
    }

    public boolean isExpired(Instant now, Duration idleThreshold) {
        boolean hardCapExpired = !now.isBefore(sessionExpiresAt);
        boolean idleExpired = !Duration.between(lastActivityAt, now).minus(idleThreshold).isNegative();
        return hardCapExpired || idleExpired;
    }

    public void resetFor(Instant startedAt, Instant expiresAt, int initialBoxTarget) {
        this.sessionStartedAt = startedAt;
        this.sessionExpiresAt = expiresAt;
        this.lastActivityAt = startedAt;
        this.sessionValidTapCount = 0L;
        this.boxesDroppedInSession = 0;
        this.nextBoxTarget = initialBoxTarget;
    }

    public void recordActivity(Instant now) {
        this.lastActivityAt = now;
    }

    public void addValidTaps(long count) {
        this.sessionValidTapCount += count;
    }

    public boolean hasReachedBoxTarget() {
        return sessionValidTapCount >= nextBoxTarget;
    }

    public void advanceBoxTarget(int nextTarget) {
        this.boxesDroppedInSession += 1;
        this.nextBoxTarget = nextTarget;
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
