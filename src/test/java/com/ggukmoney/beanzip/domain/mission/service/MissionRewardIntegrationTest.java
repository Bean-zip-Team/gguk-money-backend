package com.ggukmoney.beanzip.domain.mission.service;

import com.ggukmoney.beanzip.domain.mission.entity.MissionReward;
import com.ggukmoney.beanzip.domain.mission.repository.MissionRewardRepository;
import com.ggukmoney.beanzip.support.FullStackIntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
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

    private UUID savedUserId() {
        UUID userId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO app_user (id, status, onboarding_reward_claimed, created_at, updated_at)
                VALUES (?, 'ACTIVE', false, now(), now())
                """, userId);
        return userId;
    }
}
