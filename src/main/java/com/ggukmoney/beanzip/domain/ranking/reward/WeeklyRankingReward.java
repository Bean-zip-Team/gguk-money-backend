package com.ggukmoney.beanzip.domain.ranking.reward;

import com.ggukmoney.beanzip.domain.ranking.entity.RankingSeason;
import com.ggukmoney.beanzip.domain.user.entity.AppUser;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Index;
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

import java.time.Instant;
import java.util.UUID;

@Getter
@Entity
@Table(
        name = "weekly_ranking_reward",
        indexes = {
                @Index(name = "ix_weekly_ranking_reward_season_rank", columnList = "season_id, reward_rank"),
                @Index(name = "ix_weekly_ranking_reward_user_status_expires", columnList = "user_id, status, expires_at")
        },
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_weekly_ranking_reward_season_user", columnNames = {"season_id", "user_id"}),
                @UniqueConstraint(name = "uq_weekly_ranking_reward_season_rank", columnNames = {"season_id", "reward_rank"})
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WeeklyRankingReward {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true)
    private UUID publicId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "season_id", nullable = false)
    private RankingSeason season;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @Column(name = "source_final_rank", nullable = false)
    private Long sourceFinalRank;

    @Column(name = "reward_rank", nullable = false)
    private Integer rewardRank;

    @Column(name = "final_score", nullable = false)
    private Long finalScore;

    @Column(name = "point_amount", nullable = false)
    private Long pointAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private Status status;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "claimed_at")
    private Instant claimedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public enum Status {
        OPENED,
        CLAIMED
    }

    public static WeeklyRankingReward open(
            RankingSeason season,
            AppUser user,
            long sourceFinalRank,
            int rewardRank,
            long finalScore,
            long pointAmount,
            Instant expiresAt
    ) {
        if (season == null || user == null || expiresAt == null) {
            throw new IllegalArgumentException("season, user, and expiresAt are required");
        }
        if (sourceFinalRank <= 0 || rewardRank <= 0 || finalScore <= 0 || pointAmount <= 0) {
            throw new IllegalArgumentException("ranks, score, and amount must be positive");
        }
        WeeklyRankingReward reward = new WeeklyRankingReward();
        reward.season = season;
        reward.user = user;
        reward.sourceFinalRank = sourceFinalRank;
        reward.rewardRank = rewardRank;
        reward.finalScore = finalScore;
        reward.pointAmount = pointAmount;
        reward.status = Status.OPENED;
        reward.expiresAt = expiresAt;
        return reward;
    }

    public boolean isExpired(Instant now) {
        return !expiresAt.isAfter(now);
    }

    public void claim(Instant now) {
        if (status != Status.OPENED) {
            throw new IllegalStateException("only opened weekly ranking rewards can be claimed");
        }
        if (isExpired(now)) {
            throw new IllegalStateException("weekly ranking reward is expired");
        }
        status = Status.CLAIMED;
        claimedAt = now;
    }

    @PrePersist
    void prePersist() {
        validateState();
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
        validateState();
        updatedAt = Instant.now();
    }

    private void validateState() {
        if (status == Status.OPENED && claimedAt != null) {
            throw new IllegalStateException("opened reward cannot have claimedAt");
        }
        if (status == Status.CLAIMED && claimedAt == null) {
            throw new IllegalStateException("claimed reward requires claimedAt");
        }
    }
}
