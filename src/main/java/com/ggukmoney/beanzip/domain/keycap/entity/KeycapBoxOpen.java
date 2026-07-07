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
        name = "keycap_box_open",
        uniqueConstraints = @UniqueConstraint(name = "ux_keycap_box_open_idem", columnNames = {"user_id", "idempotency_key"})
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
    @Column(name = "open_method", nullable = false, length = 20)
    private OpenMethod openMethod;

    @Column(name = "ad_reward_id", length = 255)
    private String adRewardId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "keycap_id", nullable = false)
    private Keycap keycap;

    @Column(name = "shard_count", nullable = false)
    private Integer shardCount;

    @Column(name = "boost_applied", nullable = false)
    private boolean boostApplied = false;

    @Column(name = "completed", nullable = false)
    private boolean completed = false;

    @Column(name = "idempotency_key", nullable = false, length = 100)
    private String idempotencyKey;

    @Column(name = "opened_at", nullable = false)
    private Instant openedAt;

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
