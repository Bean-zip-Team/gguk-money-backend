package com.ggukmoney.beanzip.domain.onboarding.entity;

import com.ggukmoney.beanzip.domain.keycap.entity.Keycap;
import com.ggukmoney.beanzip.domain.user.entity.AppUser;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Check;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Getter
@Entity
@Table(
        name = "onboarding_reward_attempt",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_onboarding_reward_attempt_public_id", columnNames = "public_id"),
                @UniqueConstraint(name = "uq_onboarding_reward_attempt_tap_session", columnNames = "tap_session_id")
        },
        indexes = {
                @Index(name = "ix_onboarding_reward_attempt_expires_at", columnList = "expires_at"),
                @Index(name = "ix_onboarding_reward_attempt_reward_keycap", columnList = "reward_keycap_id"),
                @Index(name = "ix_onboarding_reward_attempt_claimed_user", columnList = "claimed_user_id")
        }
)
@Check(constraints = """
        accepted_tap_count >= 0
        and reward_point_amount >= 0
        and (
            status <> 'OPENED'
            or (claimed_user_id is null and claimed_at is null)
        )
        and (
            status <> 'CLAIMED'
            or (claimed_user_id is not null and claimed_at is not null)
        )
        """)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OnboardingRewardAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false)
    private UUID publicId;

    @Column(name = "tap_session_id", nullable = false)
    private UUID tapSessionId;

    @Column(name = "request_hash", nullable = false, length = 255)
    private String requestHash;

    @Column(name = "accepted_tap_count", nullable = false)
    private Integer acceptedTapCount;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reward_keycap_id", nullable = false)
    private Keycap rewardKeycap;

    @Column(name = "reward_point_amount", nullable = false)
    private Integer rewardPointAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private Status status;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "opened_at", nullable = false)
    private Instant openedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "claimed_user_id")
    private AppUser claimedUser;

    @Column(name = "claimed_at")
    private Instant claimedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static OnboardingRewardAttempt open(
            UUID tapSessionId,
            String requestHash,
            int acceptedTapCount,
            Keycap rewardKeycap,
            int rewardPointAmount,
            Instant openedAt,
            Instant expiresAt
    ) {
        OnboardingRewardAttempt attempt = new OnboardingRewardAttempt();
        attempt.tapSessionId = tapSessionId;
        attempt.requestHash = requestHash;
        attempt.acceptedTapCount = acceptedTapCount;
        attempt.rewardKeycap = rewardKeycap;
        attempt.rewardPointAmount = rewardPointAmount;
        attempt.status = Status.OPENED;
        attempt.openedAt = openedAt;
        attempt.expiresAt = expiresAt;
        return attempt;
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

    public void claim(AppUser user, Instant claimedAt) {
        Objects.requireNonNull(user, "user must not be null.");
        Objects.requireNonNull(claimedAt, "claimedAt must not be null.");
        if (status != Status.OPENED) {
            throw new IllegalStateException("Only opened onboarding reward attempts can be claimed.");
        }
        if (claimedUser != null || this.claimedAt != null) {
            throw new IllegalStateException("Onboarding reward attempt is already claimed.");
        }
        if (!expiresAt.isAfter(claimedAt)) {
            throw new IllegalStateException("Onboarding reward attempt is expired.");
        }
        status = Status.CLAIMED;
        claimedUser = user;
        this.claimedAt = claimedAt;
    }

    public enum Status {
        OPENED,
        CLAIMED
    }
}
