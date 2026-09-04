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

    /**
     * 세션은 마지막 탭으로부터 유휴 시간이 지나면 만료된다. {@code sessionExpiresAt} 은
     * 세션 시작 시각에 못 박힌 하드캡이 아니라 탭이 들어올 때마다 뒤로 밀리는 유휴 마감이다.
     */
    public boolean isExpired(Instant now) {
        return !now.isBefore(sessionExpiresAt);
    }

    public void resetFor(Instant startedAt, Instant expiresAt, int initialBoxTarget) {
        this.sessionStartedAt = startedAt;
        this.sessionExpiresAt = expiresAt;
        this.lastActivityAt = startedAt;
        this.sessionValidTapCount = 0L;
        this.boxesDroppedInSession = 0;
        this.nextBoxTarget = initialBoxTarget;
    }

    /**
     * 탭이 실제로 인정됐을 때만 부른다. 유휴 마감을 {@code now + idleTimeoutSeconds} 로 다시 민다.
     *
     * <p>조회성 요청에서 부르면 안 된다. 상태 조회만 반복해도 세션이 영원히 살아남아
     * 유휴 만료가 무력화된다.
     */
    public void recordActivity(Instant now, int idleTimeoutSeconds) {
        this.lastActivityAt = now;
        this.sessionExpiresAt = now.plusSeconds(idleTimeoutSeconds);
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
