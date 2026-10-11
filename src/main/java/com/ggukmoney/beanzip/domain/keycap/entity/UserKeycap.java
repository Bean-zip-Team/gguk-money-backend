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
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * 유저가 보유한 키캡 한 종 (BEA-329).
 *
 * <p>행이 있으면 보유다. 같은 키캡이 또 뽑히면 {@link #levelUp()} 으로 레벨만 오른다 — 등급 무관,
 * 상한 없음. 새 코드는 {@link Status#COMPLETED} 만 만들지만, 무중단 배포 중 구 코드가 남긴
 * {@link Status#IN_PROGRESS} 행을 읽을 수 있어야 하므로 값은 남겨 둔다. 그런 행은 "미보유"로 취급하고,
 * 뽑기에서 걸리면 {@link #convertLegacyToOwned(Instant)} 로 그 자리에서 보유로 바꾼다.
 */
@Getter
@Entity
@Table(
        name = "user_keycap",
        uniqueConstraints = @UniqueConstraint(name = "uq_user_keycap_user_keycap", columnNames = {"user_id", "keycap_id"}),
        indexes = @Index(name = "ix_user_keycap_user_status", columnList = "user_id, status")
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

    @Column(name = "level", nullable = false)
    private Integer level = 1;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private Status status = Status.COMPLETED;

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

    /**
     * 처음 얻은 키캡. Lv1 로 시작한다.
     *
     * <p>{@code completedAt} 은 키캡 5개 미션이 {@code completedAt > launchAt} 으로 소급을 막는 기준이다.
     * 여기서만 찍고 이후에는 바꾸지 않는다.
     */
    public static UserKeycap createOwned(AppUser user, Keycap keycap, Instant acquiredAt) {
        Objects.requireNonNull(user, "user must not be null.");
        Objects.requireNonNull(keycap, "keycap must not be null.");
        Objects.requireNonNull(acquiredAt, "acquiredAt must not be null.");

        UserKeycap userKeycap = new UserKeycap();
        userKeycap.user = user;
        userKeycap.keycap = keycap;
        userKeycap.level = 1;
        userKeycap.status = Status.COMPLETED;
        userKeycap.completedAt = acquiredAt;
        userKeycap.equipped = false;
        return userKeycap;
    }

    public boolean isCompleted() {
        return status == Status.COMPLETED;
    }

    /** 구 코드(상자 개봉)가 남긴 진행 중 행. 마이그레이션 B 가 쓸어 담기 전까지 잠깐 존재한다. */
    public boolean isLegacyInProgress() {
        return status == Status.IN_PROGRESS;
    }

    /**
     * 진행 중 행을 뽑기로 얻은 것으로 바꾼다. Lv1 로 시작하고 {@code completedAt} 을 지금으로 찍는다 —
     * 이 키캡은 지금 처음 얻은 것이므로 미션 집계에도 지금 들어가는 것이 맞다.
     */
    public void convertLegacyToOwned(Instant acquiredAt) {
        Objects.requireNonNull(acquiredAt, "acquiredAt must not be null.");
        if (!isLegacyInProgress()) {
            throw new IllegalStateException("Only legacy in-progress keycaps can be converted.");
        }
        level = 1;
        status = Status.COMPLETED;
        completedAt = acquiredAt;
    }

    /** 중복 획득. 「꽝」은 없다 — 반드시 1 오른다. */
    public int levelUp() {
        level += 1;
        return level;
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
        /** 구 코드가 남긴 값. 새 코드는 만들지 않고 읽기만 한다. 마이그레이션 B 가 지운다. */
        IN_PROGRESS,
        COMPLETED
    }
}
