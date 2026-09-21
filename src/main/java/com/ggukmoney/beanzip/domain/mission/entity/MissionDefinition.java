package com.ggukmoney.beanzip.domain.mission.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 데일리 미션 정의 (BEA-299).
 *
 * <p>미션 종류·조건·보상액·단계 수가 전부 행으로 관리된다. 배포 없이 값을 바꾸는 것이 요구사항이라
 * 상수로 박지 않는다 — 보상액 변경은 {@code UPDATE} 한 줄, 미션 중단은 {@code active = false} 다.
 *
 * <p>상시 미션({@code PromotionTrigger})과는 별개다. 그쪽은 토스 포인트를 외부 API 로 지급하고
 * {@code UNIQUE (user_id, promotion_code)} 로 평생 1회가 DB 에 박혀 있어 매일 반복을 얹을 수 없다.
 */
@Getter
@Entity
@Table(
        name = "mission_definition",
        indexes = @Index(name = "ix_mission_definition_active_sort", columnList = "active, sort_order")
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MissionDefinition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "code", nullable = false, unique = true, length = 60)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(name = "mission_type", nullable = false, length = 30)
    private MissionType missionType;

    @Enumerated(EnumType.STRING)
    @Column(name = "period_type", nullable = false, length = 20)
    private PeriodType periodType;

    @Column(name = "name", nullable = false, length = 60)
    private String name;

    @Column(name = "description", length = 200)
    private String description;

    /**
     * 달성 임계치. 출석·알림 허용처럼 수치가 없는 미션은 1 이다.
     *
     * <p>0 은 허용하지 않는다(DB CHECK). 0 이면 진행도와 무관하게 전원이 즉시 달성한 것으로 잡힌다.
     */
    @Column(name = "target_value", nullable = false)
    private Long targetValue = 1L;

    @Column(name = "reward_point_amount", nullable = false)
    private Long rewardPointAmount;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public enum MissionType {
        /** 당일 첫 진입. */
        ATTENDANCE,

        /** 오늘 누른 탭 수. */
        TAP_COUNT,

        /** 어제 순위 대비 상승폭. */
        RANK_UP,

        /** 데일리 미션 알림 동의. 단발성이다. */
        NOTIFICATION_OPT_IN
    }

    public enum PeriodType {
        /** 매일 자정에 초기화된다. */
        DAILY,

        /** 한 번 달성하면 다시 나오지 않는다. */
        ONE_TIME
    }
}
