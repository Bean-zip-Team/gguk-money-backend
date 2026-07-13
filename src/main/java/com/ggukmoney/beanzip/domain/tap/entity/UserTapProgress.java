package com.ggukmoney.beanzip.domain.tap.entity;

import com.ggukmoney.beanzip.domain.user.entity.AppUser;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Getter
@Entity
@Table(name = "user_tap_progress")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserTapProgress {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true)
    private UUID publicId;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private AppUser user;

    @Column(name = "cumulative_valid_tap_count", nullable = false)
    private Long cumulativeValidTapCount = 0L;

    @Column(name = "next_point_target", nullable = false)
    private Integer nextPointTarget;

    @Column(name = "next_box_target", nullable = false)
    private Integer nextBoxTarget;

    @Version
    @Column(name = "version", nullable = false)
    private Long version = 0L;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static UserTapProgress createFor(AppUser user, int initialPointTarget, int initialBoxTarget) {
        UserTapProgress progress = new UserTapProgress();
        progress.user = user;
        progress.nextPointTarget = initialPointTarget;
        progress.nextBoxTarget = initialBoxTarget;
        return progress;
    }

    public void addValidTaps(long count) {
        this.cumulativeValidTapCount += count;
    }

    public boolean hasReachedPointTarget() {
        return cumulativeValidTapCount >= nextPointTarget;
    }

    public boolean hasReachedBoxTarget() {
        return cumulativeValidTapCount >= nextBoxTarget;
    }

    public void advancePointTarget(int nextTarget) {
        this.nextPointTarget = nextTarget;
    }

    public void advanceBoxTarget(int nextTarget) {
        this.nextBoxTarget = nextTarget;
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
