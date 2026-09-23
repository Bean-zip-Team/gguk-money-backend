package com.ggukmoney.beanzip.domain.mission.service;

import com.ggukmoney.beanzip.domain.mission.entity.MissionReward;
import com.ggukmoney.beanzip.domain.mission.repository.MissionRewardRepository;
import com.ggukmoney.beanzip.support.FullStackIntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 보상 행 생성의 경합을 실제 DB 로 확인한다.
 *
 * <p>유니크 위반을 잡아서 복구하는 방식은 모킹 테스트에서는 통과하지만 실제로는 동작하지 않는다 —
 * 위반이 나는 순간 트랜잭션이 abort 되어 같은 트랜잭션 안에서는 재조회도 커밋도 할 수 없다.
 * 그래서 이 경로는 반드시 진짜 PostgreSQL 로 검증해야 한다.
 */
class MissionRewardIntegrationTest extends FullStackIntegrationTestSupport {

    private static final Instant NOW = Instant.parse("2026-09-21T05:00:00Z");
    private static final Instant MIDNIGHT = Instant.parse("2026-09-21T15:00:00Z");
    private static final String PERIOD_KEY = "2026-09-21";

    @Autowired
    private MissionRewardService missionRewardService;

    @Autowired
    private MissionRewardRepository missionRewardRepository;

    @Autowired
    private DailyMissionNudgeService dailyMissionNudgeService;

    @Autowired
    private MissionDefinitionCatalog missionDefinitionCatalog;

    /** 알림 대상 기준이 미션 정의 수를 보므로, 알림 테스트는 정의가 실제로 적재된 상태에서 돌아야 한다. */
    private void givenDailyMissionCatalog() {
        jdbcTemplate.update("""
                INSERT INTO mission_definition
                    (code, mission_type, period_type, name, description, target_value, reward_point_amount,
                     sort_order, active, created_at, updated_at)
                VALUES
                    ('ATTENDANCE', 'ATTENDANCE', 'DAILY', '출석', '출석', 1, 100, 0, TRUE, now(), now()),
                    ('TAP_500', 'TAP_COUNT', 'DAILY', '500탭', '500탭', 500, 15, 1, TRUE, now(), now()),
                    ('TAP_1000', 'TAP_COUNT', 'DAILY', '1000탭', '1000탭', 1000, 25, 2, TRUE, now(), now()),
                    ('RANK_UP', 'RANK_UP', 'DAILY', '순위 상승', '순위 상승', 1, 50, 3, TRUE, now(), now()),
                    ('NOTIFICATION_OPT_IN', 'NOTIFICATION_OPT_IN', 'ONE_TIME', '알림 허용', '알림 허용', 1, 100, 4,
                     TRUE, now(), now())
                ON CONFLICT (code) DO NOTHING
                """);
        missionDefinitionCatalog.refresh();
    }

    @Test
    void createsTheRewardOnceEvenIfTheSameAchievementIsProcessedTwice() {
        UUID userId = savedUserId();
        List<MissionRewardService.AchievedMission> achieved = List.of(
                new MissionRewardService.AchievedMission("ATTENDANCE", PERIOD_KEY, 100, MIDNIGHT));
        MissionRewardService.RewardKey key = new MissionRewardService.RewardKey("ATTENDANCE", PERIOD_KEY);

        MissionReward first = missionRewardService.createMissing(userId, achieved, Map.of(), NOW).get(key);
        // 두 번째 호출은 이미 있는 행을 만난다. 트랜잭션이 깨지지 않고 같은 보상을 돌려줘야 한다.
        MissionReward second = missionRewardService.createMissing(userId, achieved, Map.of(), NOW).get(key);

        assertThat(second.getPublicId()).isEqualTo(first.getPublicId());
        assertThat(missionRewardRepository.findByUserIdAndPeriodKeyIn(userId, List.of(PERIOD_KEY))).hasSize(1);
    }

    @Test
    void keepsRewardsOfDifferentDaysApart() {
        UUID userId = savedUserId();
        missionRewardService.createMissing(
                userId,
                List.of(new MissionRewardService.AchievedMission("ATTENDANCE", PERIOD_KEY, 100, MIDNIGHT)),
                Map.of(),
                NOW
        );
        missionRewardService.createMissing(
                userId,
                List.of(new MissionRewardService.AchievedMission("ATTENDANCE", "2026-09-22", 100, MIDNIGHT)),
                Map.of(),
                NOW
        );

        assertThat(missionRewardRepository.findByUserIdAndPeriodKeyIn(userId, List.of(PERIOD_KEY, "2026-09-22")))
                .hasSize(2);
    }

    @Test
    void closesUnclaimedRewardsThatPassedMidnightAndLeavesTheRestAlone() {
        UUID userId = savedUserId();
        missionRewardService.createMissing(
                userId,
                List.of(
                        // 어제 자정에 만료됐어야 할 보상
                        new MissionRewardService.AchievedMission(
                                "ATTENDANCE", "2026-09-20", 100, Instant.parse("2026-09-20T15:00:00Z")),
                        // 오늘 자정에 만료될 보상
                        new MissionRewardService.AchievedMission("TAP_500", PERIOD_KEY, 15, MIDNIGHT),
                        // 단발성 보상은 만료가 없다
                        new MissionRewardService.AchievedMission(
                                "NOTIFICATION_OPT_IN", MissionReward.ONE_TIME_PERIOD_KEY, 100, null)
                ),
                Map.of(),
                NOW
        );

        missionRewardService.expireDueRewards(NOW);

        assertThat(missionRewardRepository.findByUserIdAndStatusOrderByAchievedAtDesc(
                userId, MissionReward.Status.EXPIRED))
                .extracting(MissionReward::getMissionCode)
                .containsExactly("ATTENDANCE");
        assertThat(missionRewardRepository.findByUserIdAndStatusOrderByAchievedAtDesc(
                userId, MissionReward.Status.CLAIMABLE))
                .extracting(MissionReward::getMissionCode)
                .containsExactlyInAnyOrder("TAP_500", "NOTIFICATION_OPT_IN");
    }

    @Test
    void doesNotTouchRewardsTheUserAlreadyClaimed() {
        UUID userId = savedUserId();
        MissionReward reward = missionRewardService.createMissing(
                userId,
                List.of(new MissionRewardService.AchievedMission(
                        "ATTENDANCE", "2026-09-20", 100, Instant.parse("2026-09-20T15:00:00Z"))),
                Map.of(),
                Instant.parse("2026-09-20T05:00:00Z")
        ).get(new MissionRewardService.RewardKey("ATTENDANCE", "2026-09-20"));
        missionRewardService.claim(userId, reward.getPublicId(), Instant.parse("2026-09-20T06:00:00Z"));

        missionRewardService.expireDueRewards(NOW);

        assertThat(missionRewardRepository.findByUserIdAndStatusOrderByAchievedAtDesc(
                userId, MissionReward.Status.EXPIRED)).isEmpty();
    }

    @Test
    void marksNothingTwiceWhenTheBatchRunsAgain() {
        UUID userId = savedUserId();
        missionRewardService.createMissing(
                userId,
                List.of(new MissionRewardService.AchievedMission(
                        "ATTENDANCE", "2026-09-20", 100, Instant.parse("2026-09-20T15:00:00Z"))),
                Map.of(),
                NOW
        );

        missionRewardService.expireDueRewards(NOW);
        // 배치가 두 번 돌아도(재시작·중복 실행) 같은 행을 다시 세지 않는다.
        MissionRewardService.ExpiryResult second = missionRewardService.expireDueRewards(NOW);

        assertThat(second.expiredCount()).isZero();
        assertThat(second.expiredPointAmount()).isZero();
    }

    @Test
    void closesRewardsAtTheExactExpiryInstant() {
        UUID userId = savedUserId();
        missionRewardService.createMissing(
                userId,
                List.of(new MissionRewardService.AchievedMission("TAP_500", PERIOD_KEY, 15, MIDNIGHT)),
                Map.of(),
                NOW
        );

        // 수령 경로의 만료 판정(!now.isBefore(expiresAt))과 같은 경계여야 한다. 한쪽만 어긋나면
        // 자정 정각에 "배치는 소멸시켰는데 화면은 받을 수 있다고 한다"는 틈이 생긴다.
        missionRewardService.expireDueRewards(MIDNIGHT);

        assertThat(missionRewardRepository.findByUserIdAndStatusOrderByAchievedAtDesc(
                userId, MissionReward.Status.EXPIRED))
                .extracting(MissionReward::getMissionCode)
                .containsExactly("TAP_500");
    }

    @Test
    void nudgesTheUserWhoStillHasARewardToClaimAndLeavesOutTheOneWhoFinishedEverything() {
        givenDailyMissionCatalog();
        UUID claimableUserId = savedUserId();
        UUID doneUserId = savedUserId();
        givenEveryDailyReward(claimableUserId);
        givenEveryDailyReward(doneUserId).forEach(
                reward -> missionRewardService.claim(doneUserId, reward.getPublicId(), NOW));

        assertThat(dailyMissionNudgeService.usersNeedingNudge(
                criteria(), List.of(claimableUserId, doneUserId), NOW))
                .containsExactly(claimableUserId);
    }

    @Test
    void nudgesTheUserWhoStillHasMissionsLeftEvenAfterClaimingOne() {
        givenDailyMissionCatalog();
        UUID halfwayUserId = savedUserId();
        MissionReward attendance = givenReward(halfwayUserId, "ATTENDANCE", 100);
        missionRewardService.claim(halfwayUserId, attendance.getPublicId(), NOW);

        // 받을 보상은 없지만 자정에 사라질 미션이 남아 있다. 조금 참여한 유저가 전혀 참여하지 않은
        // 유저보다 알림을 덜 받으면 안 된다.
        assertThat(dailyMissionNudgeService.usersNeedingNudge(criteria(), List.of(halfwayUserId), NOW))
                .containsExactly(halfwayUserId);
    }

    @Test
    void doesNotNudgeForAOneTimeRewardThatNeverExpires() {
        givenDailyMissionCatalog();
        UUID userId = savedUserId();
        givenEveryDailyReward(userId).forEach(
                reward -> missionRewardService.claim(userId, reward.getPublicId(), NOW));
        // 알림 동의 보상은 만료가 없다. 이걸 대상 기준에 넣으면, 알림에 동의한 유저는 오늘 할 일을
        // 다 끝내도 매일 밤 알림을 받게 된다.
        missionRewardService.createMissing(
                userId,
                List.of(new MissionRewardService.AchievedMission(
                        "NOTIFICATION_OPT_IN", MissionReward.ONE_TIME_PERIOD_KEY, 100, null)),
                Map.of(),
                NOW
        );

        assertThat(dailyMissionNudgeService.usersNeedingNudge(criteria(), List.of(userId), NOW))
                .isEmpty();
    }

    @Test
    void keepsNudgingWhenARankUpRewardStandsInForAMissionStillLeft() {
        givenDailyMissionCatalog();
        UUID userId = savedUserId();
        // 보상 행은 세 개지만 그중 하나가 랭킹 상승이다. 기준이 되는 미션 중에서는 1000탭이 남아 있다.
        List.of(
                givenReward(userId, "ATTENDANCE", 100),
                givenReward(userId, "TAP_500", 15),
                givenReward(userId, "RANK_UP", 50)
        ).forEach(reward -> missionRewardService.claim(userId, reward.getPublicId(), NOW));

        assertThat(dailyMissionNudgeService.usersNeedingNudge(criteria(), List.of(userId), NOW))
                .containsExactly(userId);
    }

    @Test
    void doesNotNudgeForARewardThatAlreadyPassedItsExpiry() {
        givenDailyMissionCatalog();
        UUID userId = savedUserId();
        givenEveryDailyReward(userId);

        // 자정 배치가 아직 돌지 않아 상태는 CLAIMABLE 이지만 이미 받을 수 없는 보상이다.
        // 이걸로 알림을 보내면 유저는 들어와서 받을 게 없는 상태를 본다.
        assertThat(dailyMissionNudgeService.usersNeedingNudge(criteria(), List.of(userId), MIDNIGHT))
                .isEmpty();
    }

    private DailyMissionNudgeService.NudgeCriteria criteria() {
        return dailyMissionNudgeService.criteriaOf(LocalDate.parse(PERIOD_KEY));
    }

    /** 누구나 끝낼 수 있는 데일리 미션(출석·500탭·1000탭)을 모두 달성한 상태로 만든다. */
    private List<MissionReward> givenEveryDailyReward(UUID userId) {
        return List.of(
                givenReward(userId, "ATTENDANCE", 100),
                givenReward(userId, "TAP_500", 15),
                givenReward(userId, "TAP_1000", 25)
        );
    }

    private MissionReward givenReward(UUID userId, String missionCode, long rewardPointAmount) {
        return missionRewardService.createMissing(
                userId,
                List.of(new MissionRewardService.AchievedMission(missionCode, PERIOD_KEY, rewardPointAmount, MIDNIGHT)),
                Map.of(),
                NOW
        ).get(new MissionRewardService.RewardKey(missionCode, PERIOD_KEY));
    }

    private UUID savedUserId() {
        UUID userId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO app_user (id, status, onboarding_reward_claimed, created_at, updated_at)
                VALUES (?, 'ACTIVE', false, now(), now())
                """, userId);
        jdbcTemplate.update("""
                INSERT INTO point_account
                    (public_id, user_id, balance, lifetime_earned, lifetime_spent, version, created_at, updated_at)
                VALUES (?, ?, 0, 0, 0, 0, now(), now())
                """, UUID.randomUUID(), userId);
        return userId;
    }
}
