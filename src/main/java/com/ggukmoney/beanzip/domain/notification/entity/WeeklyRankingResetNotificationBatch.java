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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Getter
@Entity
@Table(name = "weekly_ranking_reset_notification_batch",
        uniqueConstraints = @UniqueConstraint(name = "uq_weekly_reset_notification_batch_season", columnNames = "season_id"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WeeklyRankingResetNotificationBatch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "season_id", nullable = false, updatable = false)
    private Long seasonId;

    @Column(name = "preference_cursor", nullable = false)
    private long preferenceCursor;

    @Column(name = "enqueue_completed", nullable = false)
    private boolean enqueueCompleted;

    @Column(name = "completed", nullable = false)
    private boolean completed;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static WeeklyRankingResetNotificationBatch start(Long seasonId, Instant now) {
        if (seasonId == null || seasonId <= 0 || now == null) {
            throw new IllegalArgumentException("seasonId and now are required");
        }
        WeeklyRankingResetNotificationBatch batch = new WeeklyRankingResetNotificationBatch();
        batch.seasonId = seasonId;
        batch.preferenceCursor = 0L;
        batch.enqueueCompleted = false;
        batch.completed = false;
        batch.createdAt = now;
        batch.updatedAt = now;
        return batch;
    }

    public void recordPreferencePage(long lastPreferenceId, boolean lastPage, Instant now) {
        if (completed || enqueueCompleted || lastPreferenceId < preferenceCursor || now == null) {
            throw new IllegalStateException("weekly reset batch cannot advance preference cursor");
        }
        preferenceCursor = lastPreferenceId;
        enqueueCompleted = lastPage;
        updatedAt = now;
    }

    public void markEnqueueCompleted(Instant now) {
        if (completed || now == null) {
            throw new IllegalStateException("weekly reset batch cannot complete enqueueing");
        }
        enqueueCompleted = true;
        updatedAt = now;
    }

    public void markCompleted(Instant now) {
        if (!enqueueCompleted || completed || now == null) {
            throw new IllegalStateException("weekly reset batch is not ready to complete");
        }
        completed = true;
        completedAt = now;
        updatedAt = now;
    }

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        updatedAt = Instant.now();
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }
}
