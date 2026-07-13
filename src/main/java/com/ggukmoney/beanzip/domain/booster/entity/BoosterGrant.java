package com.ggukmoney.beanzip.domain.booster.entity;

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

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Getter
@Entity
@Table(
        name = "booster_grant",
        uniqueConstraints = @UniqueConstraint(name = "uq_booster_grant_user_date_sequence", columnNames = {"user_id", "grant_date", "daily_sequence"})
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BoosterGrant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true)
    private UUID publicId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @Column(name = "grant_date", nullable = false)
    private LocalDate grantDate;

    @Column(name = "daily_sequence", nullable = false)
    private Integer dailySequence;

    @Column(name = "multiplier", nullable = false, precision = 5, scale = 2)
    private BigDecimal multiplier = new BigDecimal("2.0");

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private Status status = Status.ACTIVE;

    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static BoosterGrant activate(AppUser user, LocalDate grantDate, int dailySequence, Duration duration) {
        BoosterGrant grant = new BoosterGrant();
        grant.user = user;
        grant.grantDate = grantDate;
        grant.dailySequence = dailySequence;
        grant.startsAt = Instant.now();
        grant.expiresAt = grant.startsAt.plus(duration);
        return grant;
    }

    public boolean isActiveAt(Instant now) {
        return status == Status.ACTIVE && now.isBefore(expiresAt);
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

    public enum Status {
        ACTIVE,
        EXPIRED,
        CANCELED
    }
}
