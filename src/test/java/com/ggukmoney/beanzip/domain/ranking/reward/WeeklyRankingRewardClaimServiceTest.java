package com.ggukmoney.beanzip.domain.ranking.reward;

import com.ggukmoney.beanzip.domain.point.entity.PointAccount;
import com.ggukmoney.beanzip.domain.point.service.PointAccountService;
import com.ggukmoney.beanzip.domain.point.service.PointLedgerService;
import com.ggukmoney.beanzip.domain.ranking.boost.SystemRankingBoostPolicy;
import com.ggukmoney.beanzip.domain.ranking.entity.RankingSeason;
import com.ggukmoney.beanzip.domain.user.entity.AppUser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(OutputCaptureExtension.class)
class WeeklyRankingRewardClaimServiceTest {

    private final WeeklyRankingRewardRepository rewards = mock(WeeklyRankingRewardRepository.class);
    private final PointAccountService accounts = mock(PointAccountService.class);
    private final PointLedgerService ledgers = mock(PointLedgerService.class);
    private final SystemRankingBoostPolicy internalAccountPolicy = mock(SystemRankingBoostPolicy.class);
    private final Instant now = Instant.parse("2026-09-22T00:00:00Z");
    private final WeeklyRankingRewardClaimService service = new WeeklyRankingRewardClaimService(
            rewards, accounts, ledgers, internalAccountPolicy, Clock.fixed(now, ZoneOffset.UTC));

    @Test
    void claimDoesNotRecheckConsentAndCreditsOnlyOnce() {
        UUID userId = UUID.randomUUID();
        AppUser user = mock(AppUser.class);
        WeeklyRankingReward reward = reward(user, now.plusSeconds(60));
        UUID rewardId = reward.getPublicId();
        PointAccount account = mock(PointAccount.class);
        when(rewards.findOwnedByPublicIdForUpdate(rewardId, userId)).thenReturn(Optional.of(reward));
        when(internalAccountPolicy.load(now)).thenReturn(Optional.of(policy(Set.of(UUID.randomUUID()))));
        when(accounts.credit(userId, 10_000L)).thenReturn(account);

        assertThat(service.claim(userId, rewardId).getStatus()).isEqualTo(WeeklyRankingReward.Status.CLAIMED);
        assertThat(service.claim(userId, rewardId).getStatus()).isEqualTo(WeeklyRankingReward.Status.CLAIMED);

        verify(accounts, times(1)).credit(userId, 10_000L);
        verify(ledgers, times(1)).recordCredit(account, user, 10_000L, "WEEKLY_RANKING_REWARD", rewardId);
        verify(internalAccountPolicy, times(1)).load(now);
    }

    @Test
    void internalAccountClaimBecomesClaimedWithoutPointCreditAndWritesAuditLog(CapturedOutput output) {
        UUID userId = UUID.randomUUID();
        WeeklyRankingReward reward = reward(mock(AppUser.class), now.plusSeconds(60));
        when(rewards.findOwnedByPublicIdForUpdate(reward.getPublicId(), userId)).thenReturn(Optional.of(reward));
        when(internalAccountPolicy.load(now)).thenReturn(Optional.of(policy(Set.of(userId))));

        assertThat(service.claim(userId, reward.getPublicId()).getStatus())
                .isEqualTo(WeeklyRankingReward.Status.CLAIMED);
        assertThat(service.claim(userId, reward.getPublicId()).getStatus())
                .isEqualTo(WeeklyRankingReward.Status.CLAIMED);

        verifyNoInteractions(accounts, ledgers);
        verify(internalAccountPolicy, times(1)).load(now);
        assertThat(output).contains(
                "WEEKLY_RANKING_REWARD_UNPAID",
                "reason=INTERNAL_ACCOUNT",
                "userId=" + userId,
                "rewardId=" + reward.getPublicId(),
                "pointAmount=10000"
        );
        assertThat(output.getOut()).containsOnlyOnce("WEEKLY_RANKING_REWARD_UNPAID reason=INTERNAL_ACCOUNT");
    }

    @Test
    void unavailableInternalAccountPolicyFailsClosedBeforeClaimOrCredit() {
        UUID userId = UUID.randomUUID();
        WeeklyRankingReward reward = reward(mock(AppUser.class), now.plusSeconds(60));
        when(rewards.findOwnedByPublicIdForUpdate(reward.getPublicId(), userId)).thenReturn(Optional.of(reward));
        when(internalAccountPolicy.load(now)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.claim(userId, reward.getPublicId()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("503 SERVICE_UNAVAILABLE")
                .hasMessageContaining("RANKING_REWARD_INTERNAL_ACCOUNT_POLICY_UNAVAILABLE");

        assertThat(reward.getStatus()).isEqualTo(WeeklyRankingReward.Status.PENDING);
        verify(accounts, never()).credit(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyLong());
        verifyNoInteractions(ledgers);
    }

    @Test
    void emptyInternalAccountListFailsClosedBeforeClaimOrCredit() {
        UUID userId = UUID.randomUUID();
        WeeklyRankingReward reward = reward(mock(AppUser.class), now.plusSeconds(60));
        when(rewards.findOwnedByPublicIdForUpdate(reward.getPublicId(), userId)).thenReturn(Optional.of(reward));
        when(internalAccountPolicy.load(now)).thenReturn(Optional.of(policy(Set.of())));

        assertThatThrownBy(() -> service.claim(userId, reward.getPublicId()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("503 SERVICE_UNAVAILABLE")
                .hasMessageContaining("RANKING_REWARD_INTERNAL_ACCOUNT_POLICY_UNAVAILABLE");

        assertThat(reward.getStatus()).isEqualTo(WeeklyRankingReward.Status.PENDING);
        verifyNoInteractions(accounts, ledgers);
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

    private SystemRankingBoostPolicy.Snapshot policy(Set<UUID> internalUserIds) {
        return new SystemRankingBoostPolicy.Snapshot(false, internalUserIds, 1_000L, 200, 500);
    }
}
