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

    /** 커트오프 시점의 누적 탭 수. null 이면 아직 기준이 잡히지 않았다는 뜻이다. */
    @Column(name = "promotion_tap_baseline")
    private Long promotionTapBaseline;

    @Column(name = "next_point_target", nullable = false)
    private Integer nextPointTarget;

    @Version
    @Column(name = "version", nullable = false)
    private Long version = 0L;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static UserTapProgress createFor(AppUser user, int initialPointTarget) {
        UserTapProgress progress = new UserTapProgress();
        progress.user = user;
        progress.nextPointTarget = initialPointTarget;
        return progress;
    }

    /**
     * 프로모션 소급 방지용 기준값. 커트오프 시점의 누적 탭 수를 한 번만 박는다 (BEA-278).
     *
     * <p>탭에는 시각이 없고 누적 카운터뿐이라 "커트오프 이후 발생분"을 직접 셀 수 없다.
     * 대신 기준값을 저장해 두고 차이를 본다. 이미 값이 있으면 덮어쓰지 않는다 — 덮으면 진행이
     * 초기화돼 이미 채운 유저가 처음부터 다시 모아야 한다.
     *
     * @return 적용된 기준값
     */
    public long ensurePromotionTapBaseline(long candidate) {
        if (promotionTapBaseline == null) {
            promotionTapBaseline = candidate;
        }
        return promotionTapBaseline;
    }

    /** 기준값이 아직 없으면 소급 방지를 보장할 수 없으므로 0 이 아니라 비어 있음으로 다룬다. */
    public boolean hasPromotionTapBaseline() {
        return promotionTapBaseline != null;
    }

    public void addValidTaps(long count) {
        this.cumulativeValidTapCount += count;
    }

    public boolean hasReachedPointTarget() {
        return cumulativeValidTapCount >= nextPointTarget;
    }

    public void advancePointTarget(int nextTarget) {
        this.nextPointTarget = nextTarget;
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
