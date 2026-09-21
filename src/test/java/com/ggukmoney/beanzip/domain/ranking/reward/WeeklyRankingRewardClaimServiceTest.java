package com.ggukmoney.beanzip.domain.ranking.reward;

import com.ggukmoney.beanzip.domain.point.entity.PointAccount;
import com.ggukmoney.beanzip.domain.point.service.PointAccountService;
import com.ggukmoney.beanzip.domain.point.service.PointLedgerService;
import com.ggukmoney.beanzip.domain.ranking.entity.RankingSeason;
import com.ggukmoney.beanzip.domain.user.entity.AppUser;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WeeklyRankingRewardClaimServiceTest {

    private final WeeklyRankingRewardRepository rewards = mock(WeeklyRankingRewardRepository.class);
    private final PointAccountService accounts = mock(PointAccountService.class);
    private final PointLedgerService ledgers = mock(PointLedgerService.class);
    private final Instant now = Instant.parse("2026-09-22T00:00:00Z");
    private final WeeklyRankingRewardClaimService service = new WeeklyRankingRewardClaimService(
            rewards, accounts, ledgers, Clock.fixed(now, ZoneOffset.UTC));

    @Test
    void claimDoesNotRecheckConsentAndCreditsOnlyOnce() {
        UUID userId = UUID.randomUUID();
        AppUser user = mock(AppUser.class);
        WeeklyRankingReward reward = reward(user, now.plusSeconds(60));
        UUID rewardId = reward.getPublicId();
        PointAccount account = mock(PointAccount.class);
        when(rewards.findOwnedByPublicIdForUpdate(rewardId, userId)).thenReturn(Optional.of(reward));
        when(accounts.credit(userId, 10_000L)).thenReturn(account);

        assertThat(service.claim(userId, rewardId).getStatus()).isEqualTo(WeeklyRankingReward.Status.CLAIMED);
        assertThat(service.claim(userId, rewardId).getStatus()).isEqualTo(WeeklyRankingReward.Status.CLAIMED);

        verify(accounts, times(1)).credit(userId, 10_000L);
        verify(ledgers, times(1)).recordCredit(account, user, 10_000L, "WEEKLY_RANKING_REWARD", rewardId);
    }

    @Test
    void claimAtExpiryPersistsExpiredStateAndReturnsConflict() {
        UUID userId = UUID.randomUUID();
        WeeklyRankingReward reward = reward(mock(AppUser.class), now);
        when(rewards.findOwnedByPublicIdForUpdate(reward.getPublicId(), userId)).thenReturn(Optional.of(reward));

        assertThatThrownBy(() -> service.claim(userId, reward.getPublicId()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("409 CONFLICT");
        assertThat(reward.getStatus()).isEqualTo(WeeklyRankingReward.Status.EXPIRED);
    }

    private WeeklyRankingReward reward(AppUser user, Instant expiresAt) {
        WeeklyRankingReward reward = WeeklyRankingReward.open(
                mock(RankingSeason.class), user, 1L, 1, 100L, 10_000L, expiresAt);
        reward.prePersist();
        return reward;
    }
}
