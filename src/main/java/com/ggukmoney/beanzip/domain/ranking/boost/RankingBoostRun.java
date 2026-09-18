package com.ggukmoney.beanzip.domain.ranking.boost;

import jakarta.persistence.*;
import lombok.*;
import java.time.*;
import java.util.UUID;

/** Durable daily plan, SYSTEM_RANKING_BOOST audit, season exclusion evidence and dispatch outbox. */
@Getter
@Entity
@Table(name = "ranking_boost_run", uniqueConstraints = @UniqueConstraint(name = "uq_ranking_boost_run_date", columnNames = "run_date"),
        indexes = @Index(name = "ix_ranking_boost_run_season_user", columnList = "season_id, selected_user_id"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RankingBoostRun {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "run_date", nullable = false, updatable = false) private LocalDate runDate;
    @Column(name = "scheduled_at", nullable = false, updatable = false) private Instant scheduledAt;
    @Column(name = "event_type", nullable = false, updatable = false, length = 60) private String eventType = "SYSTEM_RANKING_BOOST";
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private Status status = Status.PLANNED;
    @Column(name = "season_id") private Long seasonId;
    @Column(name = "selected_user_id") private UUID selectedUserId;
    @Column(name = "leader_user_id") private UUID leaderUserId;
    @Column(name = "real_score") private Long realScore;
    @Column(name = "previous_score") private Long previousScore;
    @Column(name = "target_score") private Long targetScore;
    @Column(name = "boost_before") private Long boostBefore;
    @Column(name = "boost_after") private Long boostAfter;
    @Column(name = "score_increment") private Integer scoreIncrement;
    @Column(name = "leader_rank_before") private Long leaderRankBefore;
    @Column(name = "leader_rank_after") private Long leaderRankAfter;
    @Column(name = "policy_snapshot", columnDefinition = "text") private String policySnapshot;
    @Column(name = "applied_at") private Instant appliedAt;
    @Column(name = "skip_reason", length = 100) private String skipReason;
    @Column(name = "notification_delivery_id") private Long notificationDeliveryId;
    @Column(name = "dispatch_claimed_at") private Instant dispatchClaimedAt;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Version @Column(nullable = false) private Long version = 0L;

    public enum Status { PLANNED, APPLIED, SKIPPED }

    public static RankingBoostRun plan(LocalDate date, Instant scheduledAt, Instant now) {
        RankingBoostRun run = new RankingBoostRun();
        run.runDate = date; run.scheduledAt = scheduledAt; run.createdAt = now;
        return run;
    }

    public void skip(String reason) {
        requirePlanned(); status = Status.SKIPPED; skipReason = reason;
    }

    public void applied(Long seasonId, UUID selected, UUID leader, long real, long previous, long target,
                        long boostBefore, long boostAfter, int increment, long beforeRank, long afterRank,
                        String policy, Instant now, Long deliveryId) {
        requirePlanned();
        this.seasonId = seasonId; selectedUserId = selected; leaderUserId = leader; realScore = real;
        previousScore = previous; targetScore = target; this.boostBefore = boostBefore; this.boostAfter = boostAfter;
        scoreIncrement = increment; leaderRankBefore = beforeRank; leaderRankAfter = afterRank;
        policySnapshot = policy; appliedAt = now; notificationDeliveryId = deliveryId; status = Status.APPLIED;
    }

    public boolean claimDispatch(Instant now) {
        if (status != Status.APPLIED || notificationDeliveryId == null || dispatchClaimedAt != null) return false;
        dispatchClaimedAt = now;
        return true;
    }

    private void requirePlanned() {
        if (status != Status.PLANNED) throw new IllegalStateException("daily result is immutable");
    }
}
