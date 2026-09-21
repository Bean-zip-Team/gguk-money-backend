package com.ggukmoney.beanzip.domain.mission.repository;

import com.ggukmoney.beanzip.domain.mission.entity.MissionReward;
import jakarta.persistence.LockModeType;
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

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select reward
            from MissionReward reward
            where reward.userId = :userId
              and reward.status = com.ggukmoney.beanzip.domain.mission.entity.MissionReward.Status.CLAIMABLE
            order by reward.achievedAt desc
            """)
    List<MissionReward> findClaimablesForUpdate(@Param("userId") UUID userId);

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

    /** 자정 배치가 마감할 대상. 상태를 먼저 걸러 인덱스를 그대로 탄다. */
    List<MissionReward> findByStatusAndExpiresAtLessThanEqual(MissionReward.Status status, Instant expiresAt);
}
