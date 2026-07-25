package com.ggukmoney.beanzip.domain.ranking.entity;

import com.ggukmoney.beanzip.domain.user.entity.AppUser;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Getter
@Entity
@Table(
        name = "ranking_entry",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_ranking_entry_season_user", columnNames = {"season_id", "user_id"})
        },
        indexes = {
                @Index(name = "ix_ranking_entry_global", columnList = "season_id, score, user_id"),
                @Index(name = "ix_ranking_entry_cursor", columnList = "updated_at, id")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RankingEntry {

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

    @Column(name = "region_code", length = 40)
    private String regionCode;

    @Column(name = "score", nullable = false)
    private Long score = 0L;

    @Column(name = "score_updated_at", nullable = false)
    private Instant scoreUpdatedAt;

    @Column(name = "final_rank")
    private Long finalRank;

    @Column(name = "finalized_at")
    private Instant finalizedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version = 0L;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static RankingEntry createFor(
            RankingSeason season,
            AppUser user,
            long score,
            String regionCode,
            Instant occurredAt
    ) {
        RankingEntry entry = new RankingEntry();
        entry.season = season;
        entry.user = user;
        entry.updateScore(score, regionCode, occurredAt);
        return entry;
    }

    public void updateScore(long score, String regionCode, Instant occurredAt) {
        if (score < 0) {
            throw new IllegalArgumentException("score must not be negative");
        }
        this.score = score;
        this.regionCode = normalizeRegionCode(regionCode);
        this.scoreUpdatedAt = occurredAt == null ? Instant.now() : occurredAt;
    }

    public void touchForEligibilityChange(Instant occurredAt) {
        this.scoreUpdatedAt = occurredAt == null ? Instant.now() : occurredAt;
    }

    public boolean isParticipantEligible() {
        return user.getStatus() == AppUser.Status.ACTIVE && score > 0;
    }

    public void finalizeRank(long finalRank, Instant finalizedAt) {
        if (finalRank <= 0) {
            throw new IllegalArgumentException("finalRank must be positive");
        }
        if (finalizedAt == null) {
            throw new IllegalArgumentException("finalizedAt is required");
        }
        if (this.finalRank != null && !this.finalRank.equals(finalRank)) {
            throw new IllegalStateException("finalRank cannot be overwritten with a different value");
        }
        if (this.finalizedAt != null && !this.finalizedAt.equals(finalizedAt)) {
            throw new IllegalStateException("finalizedAt cannot be overwritten with a different value");
        }
        this.finalRank = finalRank;
        this.finalizedAt = finalizedAt;
    }

    public boolean hasFinalRankSnapshot() {
        return finalRank != null && finalizedAt != null;
    }

    @PrePersist
    void prePersist() {
        validateFinalRankSnapshot();
        Instant now = Instant.now();
        if (publicId == null) {
            publicId = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = now;
        }
        if (scoreUpdatedAt == null) {
            scoreUpdatedAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        validateFinalRankSnapshot();
        updatedAt = Instant.now();
    }

    private String normalizeRegionCode(String regionCode) {
        return regionCode == null || regionCode.isBlank() ? null : regionCode.trim();
    }

    private void validateFinalRankSnapshot() {
        if (finalRank != null && finalRank <= 0) {
            throw new IllegalStateException("finalRank must be positive");
        }
        if ((finalRank == null) != (finalizedAt == null)) {
            throw new IllegalStateException("finalRank and finalizedAt must be both null or both present");
        }
    }
}
