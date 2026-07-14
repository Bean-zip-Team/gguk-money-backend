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
import java.util.UUID;

@Getter
@Entity
@Table(
        name = "keycap_box_open",
        indexes = {
                @Index(name = "uq_keycap_box_open_user_idempotency", columnList = "user_id, idempotency_key", unique = true),
                @Index(name = "uq_keycap_box_open_ad_reward_id", columnList = "ad_reward_id", unique = true),
                @Index(name = "ix_keycap_box_open_user_opened_at", columnList = "user_id, opened_at")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class KeycapBoxOpen {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true)
    private UUID publicId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @Enumerated(EnumType.STRING)
    @Column(name = "open_method", nullable = false, length = 30)
    private OpenMethod openMethod;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "keycap_id", nullable = false)
    private Keycap keycap;

    @Column(name = "shard_count", nullable = false)
    private Integer shardCount = 1;

    @Column(name = "idempotency_key", nullable = false, length = 100)
    private String idempotencyKey;

    @Column(name = "request_hash", nullable = false, length = 255)
    private String requestHash;

    @Column(name = "ad_reward_id", length = 255)
    private String adRewardId;

    @Column(name = "completed", nullable = false)
    private boolean completed = false;

    @Column(name = "opened_at", nullable = false)
    private Instant openedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static KeycapBoxOpen createFor(
            AppUser user,
            OpenMethod openMethod,
            Keycap keycap,
            int shardCount,
            String idempotencyKey,
            String requestHash,
            String adRewardId,
            boolean completed,
            Instant openedAt
    ) {
        KeycapBoxOpen open = new KeycapBoxOpen();
        open.user = user;
        open.openMethod = openMethod;
        open.keycap = keycap;
        open.shardCount = shardCount;
        open.idempotencyKey = idempotencyKey;
        open.requestHash = requestHash;
        open.adRewardId = adRewardId;
        open.completed = completed;
        open.openedAt = openedAt;
        return open;
    }

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        if (publicId == null) {
            publicId = UUID.randomUUID();
        }
        if (openedAt == null) {
            openedAt = now;
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

    public enum OpenMethod {
        FREE,
        ADVERTISEMENT
    }
}
