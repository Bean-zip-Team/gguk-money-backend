package com.ggukmoney.beanzip.domain.ranking.reward;

import com.ggukmoney.beanzip.domain.point.repository.PointLedgerRepository;
import com.ggukmoney.beanzip.domain.point.service.PointAccountService;
import com.ggukmoney.beanzip.domain.ranking.boost.SystemRankingBoostPolicy;
import com.ggukmoney.beanzip.domain.ranking.entity.RankingSeason;
import com.ggukmoney.beanzip.domain.ranking.repository.RankingSeasonRepository;
import com.ggukmoney.beanzip.domain.user.entity.AppUser;
import com.ggukmoney.beanzip.domain.user.repository.AppUserRepository;
import com.ggukmoney.beanzip.global.config.entity.AppConfig;
import com.ggukmoney.beanzip.global.config.repository.AppConfigRepository;
import com.ggukmoney.beanzip.support.FullStackIntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
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

    @Autowired
    private AppConfigRepository appConfigRepository;

    @Test
    void concurrentClaimsCreditPointsExactlyOnce() throws Exception {
        AppUser user = userRepository.saveAndFlush(AppUser.createActive("weekly winner", null));
        pointAccountService.createFor(user);
        configureInternalUsers(List.of());
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

    @Test
    void internalAccountClaimChangesStatusWithoutBalanceOrLedgerCredit() {
        AppUser internalUser = userRepository.saveAndFlush(AppUser.createActive("internal weekly winner", null));
        pointAccountService.createFor(internalUser);
        configureInternalUsers(List.of(internalUser.getId()));
        Instant startsAt = Instant.parse("2026-09-21T15:00:00Z");
        RankingSeason season = seasonRepository.saveAndFlush(RankingSeason.activeWeekly(
                LocalDate.of(2026, 9, 29), startsAt, startsAt.plusSeconds(7 * 24 * 60 * 60)));
        WeeklyRankingReward reward = rewardRepository.saveAndFlush(WeeklyRankingReward.open(
                season, internalUser, 1L, 1, 100L, 10_000L, Instant.now().plusSeconds(60)));
        long ledgerCountBefore = pointLedgerRepository.count();

        WeeklyRankingReward claimed = claimService.claim(internalUser.getId(), reward.getPublicId());

        assertThat(claimed.getStatus()).isEqualTo(WeeklyRankingReward.Status.CLAIMED);
        assertThat(pointAccountService.getBalance(internalUser.getId())).isZero();
        assertThat(pointLedgerRepository.count()).isEqualTo(ledgerCountBefore);
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

    private void configureInternalUsers(List<UUID> internalUserIds) {
        String ids = internalUserIds.stream()
                .map(id -> "\"" + id + "\"")
                .collect(java.util.stream.Collectors.joining(","));
        appConfigRepository.saveAndFlush(AppConfig.createFor(
                SystemRankingBoostPolicy.KEY,
                "{\"enabled\":false,\"internalUserIds\":[" + ids
                        + "],\"minimumLeaderScore\":1000,\"minIncrement\":200,\"maxIncrement\":500}",
                Instant.now().minusMillis(1)
        ));
    }
}
