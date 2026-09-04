package com.ggukmoney.beanzip.domain.notification.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
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
        name = "notification_rank_state",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_notification_rank_state_user_season",
                columnNames = {"user_id", "season_id"}
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NotificationRankState {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true)
    private UUID publicId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "season_id", nullable = false)
    private Long seasonId;

    @Column(name = "baseline_rank", nullable = false)
    private Long baselineRank;

    @Column(name = "baseline_recorded_at", nullable = false)
    private Instant baselineRecordedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version = 0L;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static NotificationRankState record(UUID userId, Long seasonId, long baselineRank, Instant recordedAt) {
        NotificationRankState state = new NotificationRankState();
        state.userId = userId;
        state.updateBaseline(seasonId, baselineRank, recordedAt);
        return state;
    }

    public void updateBaseline(Long seasonId, long baselineRank, Instant recordedAt) {
        if (seasonId == null || baselineRank <= 0 || recordedAt == null) {
            throw new IllegalArgumentException("valid season, rank, and time are required");
        }
        this.seasonId = seasonId;
        this.baselineRank = baselineRank;
        this.baselineRecordedAt = recordedAt;
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
