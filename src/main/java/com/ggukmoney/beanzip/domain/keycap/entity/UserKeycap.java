package com.ggukmoney.beanzip.domain.keycap.entity;

import com.ggukmoney.beanzip.domain.user.entity.AppUser;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
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
        name = "user_keycap",
        uniqueConstraints = @UniqueConstraint(name = "uq_user_keycap_user_keycap", columnNames = {"user_id", "keycap_id"})
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserKeycap {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true)
    private UUID publicId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "keycap_id", nullable = false)
    private Keycap keycap;

    @Column(name = "shard_count", nullable = false)
    private Integer shardCount = 0;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private Status status = Status.IN_PROGRESS;

    @Column(name = "equipped", nullable = false)
    private boolean equipped = false;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

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

    public boolean isCompleted() {
        return status == Status.COMPLETED;
    }

    public void equip() {
        if (!isCompleted()) {
            throw new IllegalStateException("Only completed keycaps can be equipped.");
        }
        equipped = true;
    }

    public void unequip() {
        equipped = false;
    }

    public enum Status {
        IN_PROGRESS,
        COMPLETED
    }
}
