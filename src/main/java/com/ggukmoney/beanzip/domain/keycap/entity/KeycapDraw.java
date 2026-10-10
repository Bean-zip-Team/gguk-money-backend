package com.ggukmoney.beanzip.domain.keycap.entity;

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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * 뽑기 1회의 결과 (BEA-329). 멱등 재생과 이력 조회의 근거다.
 */
@Getter
@Entity
@Table(
        name = "keycap_draw",
        indexes = @Index(name = "uq_keycap_draw_user_idempotency", columnList = "user_id, idempotency_key", unique = true)
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class KeycapDraw {

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

    @Column(name = "shards_spent", nullable = false)
    private Integer shardsSpent;

    @Column(name = "level_after", nullable = false)
    private Integer levelAfter;

    @Column(name = "newly_acquired", nullable = false)
    private boolean newlyAcquired;

    @Column(name = "idempotency_key", nullable = false, length = 100)
    private String idempotencyKey;

    @Column(name = "drawn_at", nullable = false)
    private Instant drawnAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static KeycapDraw createFor(
            AppUser user,
            Keycap keycap,
            int shardsSpent,
            int levelAfter,
            boolean newlyAcquired,
            String idempotencyKey,
            Instant drawnAt
    ) {
        Objects.requireNonNull(user, "user must not be null.");
        Objects.requireNonNull(keycap, "keycap must not be null.");
        Objects.requireNonNull(idempotencyKey, "idempotencyKey must not be null.");
        Objects.requireNonNull(drawnAt, "drawnAt must not be null.");
        if (shardsSpent <= 0) {
            throw new IllegalArgumentException("shardsSpent must be positive.");
        }
        if (levelAfter <= 0) {
            throw new IllegalArgumentException("levelAfter must be positive.");
        }

        KeycapDraw draw = new KeycapDraw();
        draw.user = user;
        draw.keycap = keycap;
        draw.shardsSpent = shardsSpent;
        draw.levelAfter = levelAfter;
        draw.newlyAcquired = newlyAcquired;
        draw.idempotencyKey = idempotencyKey;
        draw.drawnAt = drawnAt;
        return draw;
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
