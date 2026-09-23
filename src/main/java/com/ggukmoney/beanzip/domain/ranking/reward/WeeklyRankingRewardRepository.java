package com.ggukmoney.beanzip.domain.ranking.reward;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.time.Instant;

public interface WeeklyRankingRewardRepository extends JpaRepository<WeeklyRankingReward, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT reward
            FROM WeeklyRankingReward reward
            JOIN FETCH reward.user
            WHERE reward.publicId = :publicId
              AND reward.user.id = :userId
            """)
    Optional<WeeklyRankingReward> findOwnedByPublicIdForUpdate(
            @Param("publicId") UUID publicId,
            @Param("userId") UUID userId
    );

    @Query("""
            SELECT reward
            FROM WeeklyRankingReward reward
            JOIN FETCH reward.user
            WHERE reward.season.id = :seasonId
            ORDER BY reward.rewardRank ASC
            """)
    List<WeeklyRankingReward> findAllWithUserBySeasonIdOrderByRewardRank(@Param("seasonId") Long seasonId);

    Optional<WeeklyRankingReward> findBySeasonIdAndUserId(Long seasonId, UUID userId);

    boolean existsBySeasonId(Long seasonId);

    long countBySeasonId(Long seasonId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT reward
            FROM WeeklyRankingReward reward
            WHERE reward.status = com.ggukmoney.beanzip.domain.ranking.reward.WeeklyRankingReward$Status.PENDING
              AND reward.expiresAt <= :now
            ORDER BY reward.id ASC
            """)
    List<WeeklyRankingReward> findExpiredPendingForUpdate(@Param("now") Instant now);

    List<WeeklyRankingReward> findByUserIdAndSeasonIdIn(UUID userId, List<Long> seasonIds);

    @Query("""
            SELECT COALESCE(SUM(reward.pointAmount), 0)
            FROM WeeklyRankingReward reward
            WHERE reward.user.id = :userId
              AND reward.status = com.ggukmoney.beanzip.domain.ranking.reward.WeeklyRankingReward$Status.CLAIMED
            """)
    long sumClaimedPointAmountByUserId(@Param("userId") UUID userId);

    void deleteBySeasonId(Long seasonId);
}
