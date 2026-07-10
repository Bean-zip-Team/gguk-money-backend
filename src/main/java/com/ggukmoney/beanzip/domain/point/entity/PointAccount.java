package com.ggukmoney.beanzip.domain.point.entity;

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
        name = "point_account",
        uniqueConstraints = @UniqueConstraint(name = "uq_point_account_id_user", columnNames = {"id", "user_id"})
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PointAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true)
    private UUID publicId;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private AppUser user;

    @Column(name = "balance", nullable = false)
    private Long balance = 0L;

    @Column(name = "lifetime_earned", nullable = false)
    private Long lifetimeEarned = 0L;

    @Column(name = "lifetime_spent", nullable = false)
    private Long lifetimeSpent = 0L;

    @Version
    @Column(name = "version", nullable = false)
    private Long version = 0L;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static PointAccount createFor(AppUser user) {
        PointAccount account = new PointAccount();
        account.user = user;
        return account;
    }

    public void credit(long amount) {
        this.balance += amount;
        this.lifetimeEarned += amount;
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
