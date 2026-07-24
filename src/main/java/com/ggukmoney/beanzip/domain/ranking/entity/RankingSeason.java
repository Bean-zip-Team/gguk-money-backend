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
import java.time.LocalDate;
import java.util.UUID;

@Getter
@Entity
@Table(name = "ranking_season")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RankingSeason {

    public static final String ALL_TIME_CODE = "ALL_TIME";
    private static final String WEEKLY_CODE_PREFIX = "WEEKLY_";

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

    public static RankingSeason activeWeekly(LocalDate weekStartDate, Instant startsAt, Instant endsAt) {
        if (weekStartDate == null) {
            throw new IllegalArgumentException("weekStartDate is required");
        }
        if (startsAt == null || endsAt == null) {
            throw new IllegalArgumentException("weekly season boundaries are required");
        }
        if (!startsAt.isBefore(endsAt)) {
            throw new IllegalArgumentException("startsAt must be before endsAt");
        }
        RankingSeason season = new RankingSeason();
        season.code = weeklyCode(weekStartDate);
        season.rankingType = RankingType.WEEKLY;
        season.status = RankingSeasonStatus.ACTIVE;
        season.startsAt = startsAt;
        season.endsAt = endsAt;
        return season;
    }

    public static String weeklyCode(LocalDate weekStartDate) {
        if (weekStartDate == null) {
            throw new IllegalArgumentException("weekStartDate is required");
        }
        return WEEKLY_CODE_PREFIX + weekStartDate.toString().replace("-", "");
    }

    public boolean isWeekly() {
        return rankingType == RankingType.WEEKLY;
    }

    public boolean contains(Instant occurredAt) {
        if (occurredAt == null) {
            return false;
        }
        if (occurredAt.isBefore(startsAt)) {
            return false;
        }
        return endsAt == null || occurredAt.isBefore(endsAt);
    }

    public void startFinalizing() {
        if (!isWeekly()) {
            throw new IllegalStateException("only weekly season can start finalizing");
        }
        if (status != RankingSeasonStatus.ACTIVE) {
            throw new IllegalStateException("only active season can start finalizing");
        }
        this.status = RankingSeasonStatus.FINALIZING;
    }

    public void close(Instant closedAt) {
        if (closedAt == null) {
            throw new IllegalArgumentException("closedAt is required");
        }
        if (status == RankingSeasonStatus.CLOSED) {
            return;
        }
        if (isWeekly() && status != RankingSeasonStatus.FINALIZING) {
            throw new IllegalStateException("weekly season must be finalizing before close");
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
