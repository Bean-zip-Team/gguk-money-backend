package com.ggukmoney.beanzip.domain.ranking.reward;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

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
}
