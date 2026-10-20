package com.ggukmoney.beanzip.domain.user.entity;

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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Getter
@Entity
@Table(
        name = "app_user",
        indexes = @Index(name = "ix_app_user_status", columnList = "status")
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private Status status = Status.ACTIVE;

    @Column(name = "nickname", columnDefinition = "text")
    private String nickname;

    @Column(name = "nickname_normalized", columnDefinition = "text")
    private String nicknameNormalized;

    @Column(name = "profile_image_url", columnDefinition = "text")
    private String profileImageUrl;

    @Column(name = "onboarding_reward_claimed", nullable = false)
    private boolean onboardingRewardClaimed = false;

    @Column(name = "onboarding_completed_at")
    private Instant onboardingCompletedAt;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @Column(name = "withdrawn_at")
    private Instant withdrawnAt;

    // 정지 이력. 탈퇴로 status 가 WITHDRAWN 으로 바뀌어도 남아서, 재로그인 복구가 정지를 지우지 못하게 한다.
    @Column(name = "suspended_at")
    private Instant suspendedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static AppUser createActive(String nickname, String profileImageUrl) {
        AppUser user = new AppUser();
        user.status = Status.ACTIVE;
        user.recordLogin(nickname, profileImageUrl);
        return user;
    }

    public boolean isWithdrawn() {
        return status == Status.WITHDRAWN;
    }

    public boolean isSuspended() {
        return status == Status.SUSPENDED || suspendedAt != null;
    }

    public void recordLogin(String nickname, String profileImageUrl) {
        if (isWithdrawn()) {
            return;
        }
        this.nickname = normalizeNullable(nickname);
        this.nicknameNormalized = normalizeNickname(nickname);
        this.profileImageUrl = normalizeNullable(profileImageUrl);
        this.lastLoginAt = Instant.now();
    }

    public void reactivate(String nickname, String profileImageUrl) {
        // 정지된 채 탈퇴했다가 재로그인하면 ACTIVE 가 아니라 정지 상태로 돌아온다.
        this.status = suspendedAt != null ? Status.SUSPENDED : Status.ACTIVE;
        this.withdrawnAt = null;
        this.nickname = normalizeNullable(nickname);
        this.nicknameNormalized = normalizeNickname(nickname);
        this.profileImageUrl = normalizeNullable(profileImageUrl);
        this.lastLoginAt = Instant.now();
    }

    public void updateProfile(String nickname, String profileImageUrl) {
        if (isWithdrawn()) {
            return;
        }
        if (nickname != null) {
            this.nickname = normalizeNullable(nickname);
            this.nicknameNormalized = normalizeNickname(nickname);
        }
        if (profileImageUrl != null) {
            this.profileImageUrl = normalizeNullable(profileImageUrl);
        }
    }

    public static String normalizeNicknameForLookup(String nickname) {
        return normalizeNickname(nickname);
    }

    public void claimOnboardingReward(Instant completedAt) {
        if (onboardingRewardClaimed) {
            return;
        }
        this.onboardingRewardClaimed = true;
        this.onboardingCompletedAt = completedAt;
    }

    public void withdraw() {
        this.status = Status.WITHDRAWN;
        this.withdrawnAt = Instant.now();
        this.nickname = "withdrawn-" + id;
        this.nicknameNormalized = null;
        this.profileImageUrl = null;
    }

    public void suspend() {
        if (suspendedAt == null) {
            this.suspendedAt = Instant.now();
        }
        if (status == Status.WITHDRAWN) {
            return;
        }
        this.status = Status.SUSPENDED;
    }

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }

    private static String normalizeNickname(String nickname) {
        String normalized = normalizeNullable(nickname);
        return normalized == null ? null : normalized.toLowerCase();
    }

    private static String normalizeNullable(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public enum Status {
        ACTIVE,
        SUSPENDED,
        WITHDRAWN
    }
}
