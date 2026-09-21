package com.ggukmoney.beanzip.domain.mission.repository;

import com.ggukmoney.beanzip.domain.mission.entity.MissionReward;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MissionRewardRepository extends JpaRepository<MissionReward, Long> {

    List<MissionReward> findByUserIdAndPeriodKeyIn(UUID userId, List<String> periodKeys);

    Optional<MissionReward> findByUserIdAndMissionCodeAndPeriodKey(UUID userId, String missionCode, String periodKey);

    List<MissionReward> findByUserIdAndStatusOrderByAchievedAtDesc(UUID userId, MissionReward.Status status);

    /** 수령 대상. 같은 보상을 동시에 두 번 받지 못하도록 행을 잠근다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select reward from MissionReward reward where reward.userId = :userId and reward.publicId = :publicId")
    Optional<MissionReward> findByUserIdAndPublicIdForUpdate(
            @Param("userId") UUID userId,
            @Param("publicId") UUID publicId
    );

    /**
     * 일괄 수령 대상.
     *
     * <p>이미 자정을 넘긴 보상은 아예 잠그지 않는다. 어차피 지급하지 않을 행인데 잠그면 같은 행을
     * 마감하는 자정 배치와 서로를 기다리게 된다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select reward
            from MissionReward reward
            where reward.userId = :userId
              and reward.status = com.ggukmoney.beanzip.domain.mission.entity.MissionReward.Status.CLAIMABLE
              and (reward.expiresAt is null or reward.expiresAt > :now)
            order by reward.achievedAt desc
            """)
    List<MissionReward> findClaimablesForUpdate(@Param("userId") UUID userId, @Param("now") Instant now);

    /**
     * 달성한 보상을 만든다. 이미 있으면 아무것도 하지 않는다.
     *
     * <p>유니크 위반을 잡아서 복구하는 방식은 쓸 수 없다 — 위반이 나는 순간 트랜잭션이 abort 되어
     * 같은 트랜잭션 안에서는 재조회도 커밋도 할 수 없다. 알림 발송이 쓰는
     * {@code INSERT ... ON CONFLICT DO NOTHING} 과 같은 방식으로 충돌 자체를 없앤다.
     *
     * @return 삽입된 행 수. 0이면 이미 있다는 뜻이다.
     */
    @Modifying
    @Query(value = """
            INSERT INTO mission_reward
                (public_id, user_id, mission_code, period_key, reward_point_amount,
                 status, achieved_at, expires_at, created_at, updated_at, version)
            VALUES
                (:publicId, :userId, :missionCode, :periodKey, :rewardPointAmount,
                 'CLAIMABLE', :achievedAt, :expiresAt, :now, :now, 0)
            ON CONFLICT ON CONSTRAINT uq_mission_reward_user_code_period DO NOTHING
            """, nativeQuery = true)
    int insertClaimableIfAbsent(
            @Param("publicId") UUID publicId,
            @Param("userId") UUID userId,
            @Param("missionCode") String missionCode,
            @Param("periodKey") String periodKey,
            @Param("rewardPointAmount") long rewardPointAmount,
            @Param("achievedAt") Instant achievedAt,
            @Param("expiresAt") Instant expiresAt,
            @Param("now") Instant now
    );

    /**
     * 자정 배치가 마감할 금액. UPDATE 전에 한 번 읽는다.
     *
     * <p>{@code expires_at} 이 없는 단발성 보상은 비교에서 자연히 빠진다.
     */
    @Query("""
            select coalesce(sum(reward.rewardPointAmount), 0)
            from MissionReward reward
            where reward.status = com.ggukmoney.beanzip.domain.mission.entity.MissionReward.Status.CLAIMABLE
              and reward.expiresAt <= :now
            """)
    long sumClaimableExpiredAmount(@Param("now") Instant now);

    /**
     * 자정을 넘긴 미수령 보상을 한 문장으로 마감한다.
     *
     * <p>엔티티로 올려 하나씩 바꾸지 않는다. 하는 일이 상태 변경뿐이라 행마다 분기도 부수 효과도
     * 없고, 한 문장이면 트랜잭션도 락도 짧게 끝난다.
     *
     * @return 마감한 행 수
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update MissionReward reward
            set reward.status = com.ggukmoney.beanzip.domain.mission.entity.MissionReward.Status.EXPIRED,
                reward.updatedAt = :now,
                reward.version = reward.version + 1
            where reward.status = com.ggukmoney.beanzip.domain.mission.entity.MissionReward.Status.CLAIMABLE
              and reward.expiresAt <= :now
            """)
    int expireDueRewards(@Param("now") Instant now);
}
