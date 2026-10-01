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
     * <p>충돌 대상은 제약 이름이 아니라 <b>컬럼</b>으로 지정한다. 운영 DDL 은 유니크 인덱스를
     * 만들 뿐 제약을 만들지 않아서, 이름으로 지정하면 운영에서만 실패한다. 테스트는 Hibernate 가
     * 스키마를 만들어 제약이 생기므로 이 차이를 잡지 못한다.
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
            ON CONFLICT (user_id, mission_code, period_key) DO NOTHING
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
     * 오늘 안에 받아야 할 보상이 남은 유저. 저녁 알림 대상을 고르는 데 쓴다(BEA-299).
     *
     * <p>오늘 기간의 보상만 센다. 단발성 보상은 만료가 없어서, 한 번 달성하고 받지 않은 유저가
     * 매일 밤 대상으로 잡히기 때문이다. 알림 동의 미션이 바로 그런 경우다 — 알림에 동의한 유저만
     * 발송 대상이므로, 오늘 할 일을 다 끝낸 유저에게도 영원히 알림이 가게 된다.
     *
     * <p>이미 자정을 넘긴 보상도 뺀다. 자정 배치가 아직 돌지 않았을 뿐 받을 수 없는 보상인데,
     * 이걸로 알림을 보내면 들어와서 받을 게 없다.
     */
    @Query("""
            select distinct reward.userId
            from MissionReward reward
            where reward.userId in :userIds
              and reward.periodKey = :periodKey
              and reward.status = com.ggukmoney.beanzip.domain.mission.entity.MissionReward.Status.CLAIMABLE
              and (reward.expiresAt is null or reward.expiresAt > :now)
            """)
    List<UUID> findUserIdsWithClaimableRewardsInPeriod(
            @Param("userIds") List<UUID> userIds,
            @Param("periodKey") String periodKey,
            @Param("now") Instant now
    );

    /**
     * 유저별로 그 기간에 쌓인 보상 행의 수. 지정한 미션만 센다.
     *
     * <p>미션 코드를 받는 이유는 알림 대상을 고를 때 기준이 되는 미션과 세는 미션이 같아야 하기
     * 때문이다. 기준에서 뺀 미션의 보상이 이 수에 섞이면 그 한 건이 다른 미션 한 건의 자리를 채운다.
     *
     * <p>"달성한 미션 수"와 정확히 같지는 않다. 보상 행은 미션 목록을 열거나 수령을 부를 때 만들어지므로,
     * 조건을 채웠지만 미션 화면을 한 번도 열지 않은 유저는 행이 없다. 알림 대상 선정에서는 그래도 맞는
     * 방향으로 동작한다 — 그런 유저는 받아 갈 보상이 남아 있어 알림을 받아야 한다.
     */
    @Query("""
            select reward.userId as userId, count(reward) as rewardCount
            from MissionReward reward
            where reward.userId in :userIds
              and reward.periodKey = :periodKey
              and reward.missionCode in :missionCodes
            group by reward.userId
            """)
    List<UserRewardCount> countRewardsInPeriod(
            @Param("userIds") List<UUID> userIds,
            @Param("periodKey") String periodKey,
            @Param("missionCodes") List<String> missionCodes
    );

    interface UserRewardCount {
        UUID getUserId();

        long getRewardCount();
    }

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
