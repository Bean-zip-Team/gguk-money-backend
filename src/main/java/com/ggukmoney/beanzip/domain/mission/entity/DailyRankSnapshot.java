package com.ggukmoney.beanzip.domain.mission.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * 하루가 끝날 때의 주간 랭킹 순위 (BEA-299).
 *
 * <p>랭킹 상승 미션이 "어제보다 몇 등 올랐는지"를 재는 기준이다. 현재 순위는 언제든 다시 계산할 수
 * 있지만 <b>지나간 순위는 남겨 두지 않으면 사라진다.</b>
 *
 * <p>{@code seasonId} 를 함께 저장한다. 주간 시즌이 바뀌는 날에는 어제 순위와 오늘 순위가 서로 다른
 * 판의 값이라 비교가 성립하지 않는다.
 */
@Getter
@Entity
@Table(
        name = "daily_rank_snapshot",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_daily_rank_snapshot_user_date",
                columnNames = {"user_id", "snapshot_date"}
        ),
        indexes = @Index(name = "ix_daily_rank_snapshot_date", columnList = "snapshot_date")
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DailyRankSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "snapshot_date", nullable = false)
    private LocalDate snapshotDate;

    @Column(name = "season_id", nullable = false)
    private Long seasonId;

    @Column(name = "rank_value", nullable = false)
    private Integer rankValue;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }
}
