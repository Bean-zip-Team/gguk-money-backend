package com.ggukmoney.beanzip.domain.mission.service;

import com.ggukmoney.beanzip.domain.mission.dto.response.MissionRewardListResponse;
import com.ggukmoney.beanzip.domain.mission.entity.MissionDefinition;
import com.ggukmoney.beanzip.domain.mission.entity.MissionReward;
import com.ggukmoney.beanzip.domain.mission.repository.MissionRewardRepository;
import com.ggukmoney.beanzip.domain.point.entity.PointAccount;
import com.ggukmoney.beanzip.domain.point.service.PointAccountService;
import com.ggukmoney.beanzip.domain.point.service.PointLedgerService;
import com.ggukmoney.beanzip.domain.user.entity.AppUser;
import com.ggukmoney.beanzip.domain.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 데일리 미션 보상의 적립과 수령 (BEA-299).
 *
 * <p>달성 즉시 지급하지 않는다. 유저가 {@code 받기} 를 눌러야 포인트가 들어가고, 받지 않은 보상은
 * 자정에 소멸한다. 그래서 달성 시점에는 수령 대기 행만 만들어 둔다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MissionRewardService {

    private static final String CREDIT_REASON = "DAILY_MISSION_REWARD";

    private final MissionRewardRepository missionRewardRepository;
    private final PointAccountService pointAccountService;
    private final PointLedgerService pointLedgerService;
    private final UserService userService;

    /**
     * 해당 기간의 보상 행을 찾아 준다.
     *
     * <p>단발성 미션의 수행 이력도 여기서 나온다 — 알림을 나중에 꺼도 미션이 되살아나지 않는 근거가
     * 동의 상태가 아니라 이 행이다. 껐다 켜기로 반복 수령할 수 없다.
     */
    @Transactional(readOnly = true)
    public Map<RewardKey, MissionReward> rewardsOf(UUID userId, List<String> periodKeys) {
        Map<RewardKey, MissionReward> rewards = new LinkedHashMap<>();
        missionRewardRepository.findByUserIdAndPeriodKeyIn(userId, periodKeys)
                .forEach(reward -> rewards.put(
                        new RewardKey(reward.getMissionCode(), reward.getPeriodKey()), reward));
        return rewards;
    }

    /**
     * 달성했는데 아직 행이 없는 미션의 보상을 만들어 준다.
     *
     * <p>유니크 위반을 잡아서 복구하지 않는다. 위반이 나는 순간 트랜잭션이 abort 되어 같은
     * 트랜잭션 안에서는 재조회도 커밋도 할 수 없기 때문이다. 충돌 자체를 만들지 않는
     * {@code ON CONFLICT DO NOTHING} 으로 넣고 다시 읽는다.
     *
     * @return 새로 만들었거나 경합에서 진 뒤 다시 읽은 보상
     */
    @Transactional
    public Map<RewardKey, MissionReward> createMissing(
            UUID userId,
            List<AchievedMission> achievedMissions,
            Map<RewardKey, MissionReward> existingRewards,
            Instant now
    ) {
        Map<RewardKey, MissionReward> created = new LinkedHashMap<>();
        for (AchievedMission achieved : achievedMissions) {
            RewardKey key = new RewardKey(achieved.missionCode(), achieved.periodKey());
            if (existingRewards.containsKey(key)) {
                continue;
            }

            missionRewardRepository.insertClaimableIfAbsent(
                    UUID.randomUUID(),
                    userId,
                    achieved.missionCode(),
                    achieved.periodKey(),
                    achieved.rewardPointAmount(),
                    now,
                    achieved.expiresAt(),
                    now
            );
            missionRewardRepository
                    .findByUserIdAndMissionCodeAndPeriodKey(userId, achieved.missionCode(), achieved.periodKey())
                    .ifPresent(reward -> created.put(key, reward));
        }
        return created;
    }

    /**
     * 상태별 보상 이력.
     *
     * <p>{@code CLAIMABLE} 조회에서는 이미 자정을 넘긴 보상을 뺀다. 자정 배치가 돌기 전이라
     * 상태만 남아 있을 뿐 받을 수 없는 보상인데, 합계에 넣으면 화면의 "받을 보상" 이 실제 지급액과
     * 어긋난다.
     */
    @Transactional(readOnly = true)
    public MissionRewardListResponse history(UUID userId, MissionReward.Status status, Instant now) {
        List<MissionReward> rewards =
                missionRewardRepository.findByUserIdAndStatusOrderByAchievedAtDesc(userId, status).stream()
                        .filter(reward -> status != MissionReward.Status.CLAIMABLE || !reward.hasExpiredAt(now))
                        .toList();

        return new MissionRewardListResponse(
                rewards.stream()
                        .map(reward -> new MissionRewardListResponse.Reward(
                                reward.getPublicId(),
                                reward.getMissionCode(),
                                reward.getPeriodKey(),
                                reward.getRewardPointAmount(),
                                MissionRewardListResponse.RewardStatus.from(reward.getStatus()),
                                reward.getAchievedAt(),
                                reward.getExpiresAt(),
                                reward.getClaimedAt()
                        ))
                        .toList(),
                rewards.stream().mapToLong(MissionReward::getRewardPointAmount).sum()
        );
    }

    @Transactional
    public ClaimResult claim(UUID userId, UUID rewardPublicId, Instant now) {
        MissionReward reward = missionRewardRepository.findByUserIdAndPublicIdForUpdate(userId, rewardPublicId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "MISSION_REWARD_NOT_FOUND"));
        if (reward.isClaimed()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "MISSION_REWARD_ALREADY_CLAIMED");
        }
        if (!reward.isClaimable() || reward.hasExpiredAt(now)) {
            throw new ResponseStatusException(HttpStatus.GONE, "MISSION_REWARD_EXPIRED");
        }

        AppUser user = userService.getById(userId);
        reward.claim(now);
        long credited = credit(user, reward);
        return new ClaimResult(1, credited, pointAccountService.getBalance(userId));
    }

    /** 미수령 보상을 한 번에 받는다. 시안의 {@code 보상 2개 모두 받기} 가 이 경로다. */
    @Transactional
    public ClaimResult claimAll(UUID userId, Instant now) {
        List<MissionReward> claimables = missionRewardRepository.findClaimablesForUpdate(userId);
        AppUser user = userService.getById(userId);

        int claimedCount = 0;
        long claimedPointAmount = 0L;
        for (MissionReward reward : claimables) {
            if (reward.hasExpiredAt(now)) {
                // 자정 배치가 아직 돌지 않았을 뿐 이미 소멸한 보상이다. 일괄 수령이 통째로 실패하면
                // 받을 수 있는 나머지까지 못 받으므로 이 건만 건너뛴다.
                continue;
            }
            reward.claim(now);
            claimedPointAmount += credit(user, reward);
            claimedCount++;
        }
        return new ClaimResult(claimedCount, claimedPointAmount, pointAccountService.getBalance(userId));
    }

    private long credit(AppUser user, MissionReward reward) {
        PointAccount account = pointAccountService.credit(user.getId(), reward.getRewardPointAmount());
        pointLedgerService.recordCredit(
                account,
                user,
                reward.getRewardPointAmount(),
                CREDIT_REASON,
                idempotencyKey(user.getId(), reward)
        );
        log.info("MISSION_REWARD_CLAIMED userId={} missionCode={} periodKey={} amount={}",
                user.getId(), reward.getMissionCode(), reward.getPeriodKey(), reward.getRewardPointAmount());
        return reward.getRewardPointAmount();
    }

    /**
     * 같은 미션·같은 날짜에는 같은 키가 나온다. 동시에 두 번 눌러도 원장의
     * {@code (user_id, idempotency_key)} 유니크 제약이 두 번째 적립을 막는다.
     */
    private static UUID idempotencyKey(UUID userId, MissionReward reward) {
        String seed = userId + "-" + reward.getMissionCode() + "-" + reward.getPeriodKey();
        return UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8));
    }

    /** 보상 하나를 가리키는 키. 같은 미션이라도 반복 단위가 다르면 다른 보상이다. */
    public record RewardKey(String missionCode, String periodKey) {
    }

    /** 달성한 미션 하나. 보상 행을 만드는 데 필요한 값만 담는다. */
    public record AchievedMission(
            String missionCode,
            String periodKey,
            long rewardPointAmount,
            Instant expiresAt
    ) {

        public static String periodKeyOf(MissionDefinition.PeriodType periodType, LocalDate today) {
            return periodType == MissionDefinition.PeriodType.DAILY
                    ? today.toString()
                    : MissionReward.ONE_TIME_PERIOD_KEY;
        }
    }

    public record ClaimResult(int claimedCount, long claimedPointAmount, long pointBalance) {
    }
}
