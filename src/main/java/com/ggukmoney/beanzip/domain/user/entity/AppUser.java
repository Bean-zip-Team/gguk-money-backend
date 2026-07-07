package com.ggukmoney.beanzip.domain.user.entity;

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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Getter
@Entity
@Table(name = "app_user")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private Status status = Status.ACTIVE;

    @Column(name = "nickname", length = 50)
    private String nickname;

    @Column(name = "nickname_normalized", length = 50)
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

    public void recordLogin(String nickname, String profileImageUrl) {
        if (isWithdrawn()) {
            return;
        }
        this.nickname = normalizeNullable(nickname);
        this.nicknameNormalized = normalizeNickname(nickname);
        this.profileImageUrl = normalizeNullable(profileImageUrl);
        this.lastLoginAt = Instant.now();
    }

    public void claimOnboardingReward() {
        this.onboardingRewardClaimed = true;
        this.onboardingCompletedAt = Instant.now();
    }

    public void withdraw() {
        this.status = Status.WITHDRAWN;
        this.withdrawnAt = Instant.now();
        this.nickname = "withdrawn-" + id;
        this.nicknameNormalized = null;
        this.profileImageUrl = null;
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
