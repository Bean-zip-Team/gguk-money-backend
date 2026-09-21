package com.ggukmoney.beanzip.domain.ranking.reward;

import com.ggukmoney.beanzip.domain.point.repository.PointLedgerRepository;
import com.ggukmoney.beanzip.domain.point.service.PointAccountService;
import com.ggukmoney.beanzip.domain.ranking.entity.RankingSeason;
import com.ggukmoney.beanzip.domain.ranking.repository.RankingSeasonRepository;
import com.ggukmoney.beanzip.domain.user.entity.AppUser;
import com.ggukmoney.beanzip.domain.user.repository.AppUserRepository;
import com.ggukmoney.beanzip.support.FullStackIntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

class WeeklyRankingRewardClaimIntegrationTest extends FullStackIntegrationTestSupport {

    @Autowired
    private WeeklyRankingRewardClaimService claimService;

    @Autowired
    private WeeklyRankingRewardRepository rewardRepository;

    @Autowired
    private RankingSeasonRepository seasonRepository;

    @Autowired
    private AppUserRepository userRepository;

    @Autowired
    private PointAccountService pointAccountService;

    @Autowired
    private PointLedgerRepository pointLedgerRepository;

    @Test
    void concurrentClaimsCreditPointsExactlyOnce() throws Exception {
        AppUser user = userRepository.saveAndFlush(AppUser.createActive("weekly winner", null));
        pointAccountService.createFor(user);
        Instant startsAt = Instant.parse("2026-09-21T15:00:00Z");
        RankingSeason season = seasonRepository.saveAndFlush(RankingSeason.activeWeekly(
                LocalDate.of(2026, 9, 22), startsAt, startsAt.plusSeconds(7 * 24 * 60 * 60)));
        WeeklyRankingReward reward = rewardRepository.saveAndFlush(WeeklyRankingReward.open(
                season, user, 1L, 1, 100L, 10_000L, Instant.now().plusSeconds(60)));
        UUID rewardId = reward.getPublicId();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<WeeklyRankingReward.Status> first = executor.submit(() -> claimAfterStart(
                    ready, start, user.getId(), rewardId));
            Future<WeeklyRankingReward.Status> second = executor.submit(() -> claimAfterStart(
                    ready, start, user.getId(), rewardId));
            ready.await();
            start.countDown();

            assertThat(first.get()).isEqualTo(WeeklyRankingReward.Status.CLAIMED);
            assertThat(second.get()).isEqualTo(WeeklyRankingReward.Status.CLAIMED);
        }

        assertThat(pointAccountService.getBalance(user.getId())).isEqualTo(10_000L);
        assertThat(pointLedgerRepository.count()).isEqualTo(1L);
        assertThat(rewardRepository.findById(reward.getId()).orElseThrow().getStatus())
                .isEqualTo(WeeklyRankingReward.Status.CLAIMED);
    }

    private WeeklyRankingReward.Status claimAfterStart(
            CountDownLatch ready,
            CountDownLatch start,
            UUID userId,
            UUID rewardId
    ) throws InterruptedException {
        ready.countDown();
        start.await();
        return claimService.claim(userId, rewardId).getStatus();
    }
}
