package com.ggukmoney.beanzip.domain.ranking.entity;

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
@Table(name = "ranking_season")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RankingSeason {

    public static final String ALL_TIME_CODE = "ALL_TIME";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true)
    private UUID publicId;

    @Column(name = "code", nullable = false, unique = true, length = 80)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(name = "ranking_type", nullable = false, length = 20)
    private RankingType rankingType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private RankingSeasonStatus status;

    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;

    @Column(name = "ends_at")
    private Instant endsAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static RankingSeason activeAllTime(Instant startsAt) {
        RankingSeason season = new RankingSeason();
        season.code = ALL_TIME_CODE;
        season.rankingType = RankingType.ALL_TIME;
        season.status = RankingSeasonStatus.ACTIVE;
        season.startsAt = startsAt;
        return season;
    }

    void close(Instant closedAt) {
        if (closedAt == null) {
            throw new IllegalArgumentException("closedAt is required");
        }
        this.status = RankingSeasonStatus.CLOSED;
        this.closedAt = closedAt;
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
