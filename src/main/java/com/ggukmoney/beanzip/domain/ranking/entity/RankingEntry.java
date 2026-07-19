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

    @PrePersist
    void prePersist() {
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
        updatedAt = Instant.now();
    }

    private String normalizeRegionCode(String regionCode) {
        return regionCode == null || regionCode.isBlank() ? null : regionCode.trim();
    }
}
