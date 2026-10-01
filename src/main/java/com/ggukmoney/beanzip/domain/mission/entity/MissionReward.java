package com.ggukmoney.beanzip.domain.mission.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.Instant;
import java.util.UUID;

/**
 * 데일리 미션 보상 (BEA-299).
 *
 * <p>달성한 순간 {@code CLAIMABLE} 로 생기고, 유저가 받으면 {@code CLAIMED}, 자정을 넘기면
 * {@code EXPIRED} 가 된다. 받지 못한 보상도 행으로 남긴다 — 소멸은 정책이지만 "얼마를 놓쳤는지"는
 * 보여줄 수 있어야 하고, 소멸 규모를 재야 정책이 과한지 판단할 수 있다.
 */
@Getter
@Entity
@Table(
        name = "mission_reward",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_mission_reward_user_code_period",
                columnNames = {"user_id", "mission_code", "period_key"}
        ),
        indexes = {
                @Index(name = "ix_mission_reward_user_status", columnList = "user_id, status"),
                @Index(name = "ix_mission_reward_status_expires", columnList = "status, expires_at")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MissionReward {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true)
    private UUID publicId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "mission_code", nullable = false, length = 60)
    private String missionCode;

    /** 반복 단위. 데일리는 {@code 2026-09-21} 같은 날짜, 단발성은 {@link #ONE_TIME_PERIOD_KEY} 다. */
    @Column(name = "period_key", nullable = false, length = 20)
    private String periodKey;

    @Column(name = "reward_point_amount", nullable = false)
    private Long rewardPointAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private Status status;

    @Column(name = "achieved_at", nullable = false)
    private Instant achievedAt;

    /** 데일리 보상은 그날 자정, 단발성 보상은 만료가 없어 {@code null} 이다. */
    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "claimed_at")
    private Instant claimedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** 같은 보상을 동시에 받으려 하면 여기서 걸린다. 변경 가능한 엔티티에 버전을 두는 것이 이 저장소 관례다. */
    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    public static final String ONE_TIME_PERIOD_KEY = "ONCE";

    public static MissionReward claimable(
            UUID userId,
            String missionCode,
            String periodKey,
            long rewardPointAmount,
            Instant achievedAt,
            Instant expiresAt
    ) {
        MissionReward reward = new MissionReward();
        reward.publicId = UUID.randomUUID();
        reward.userId = userId;
        reward.missionCode = missionCode;
        reward.periodKey = periodKey;
        reward.rewardPointAmount = rewardPointAmount;
        reward.status = Status.CLAIMABLE;
        reward.achievedAt = achievedAt;
        reward.expiresAt = expiresAt;
        return reward;
    }

    /**
     * 수령 처리. 받을 수 있는지는 호출 전에 {@link #isClaimable()} 과 {@link #hasExpiredAt(Instant)} 로
     * 판정한다 — HTTP 상태로 옮기는 일은 서비스가 한다.
     */
    public void claim(Instant now) {
        if (!isClaimable() || hasExpiredAt(now)) {
            throw new IllegalStateException("claimable reward required: status=" + status + " expiresAt=" + expiresAt);
        }
        this.status = Status.CLAIMED;
        this.claimedAt = now;
    }

    /** 자정 배치가 미수령 보상을 마감한다. 포인트는 지급하지 않는다. */
    public void expire() {
        if (status != Status.CLAIMABLE) {
            return;
        }
        this.status = Status.EXPIRED;
    }

    public boolean isClaimable() {
        return status == Status.CLAIMABLE;
    }

    public boolean isClaimed() {
        return status == Status.CLAIMED;
    }

    public boolean hasExpiredAt(Instant now) {
        return expiresAt != null && !now.isBefore(expiresAt);
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public enum Status {
        /** 달성했고 아직 받지 않았다. */
        CLAIMABLE,

        /** 받았다. */
        CLAIMED,

        /** 받지 않은 채 자정을 넘겼다. */
        EXPIRED
    }
}
