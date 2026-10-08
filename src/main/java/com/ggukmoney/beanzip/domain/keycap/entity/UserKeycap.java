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
 * 상한 없음. {@code status} 는 항상 {@link Status#COMPLETED} 이지만 완성 기준으로 세는 쿼리
 * (키캡 5개 미션 · 전체 완성 보너스)가 그대로 동작하도록 컬럼을 남겨 둔다.
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
        COMPLETED
    }
}
