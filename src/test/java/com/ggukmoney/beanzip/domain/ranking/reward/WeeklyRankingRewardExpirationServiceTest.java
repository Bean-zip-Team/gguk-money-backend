package com.ggukmoney.beanzip.domain.ranking.reward;

import com.ggukmoney.beanzip.domain.ranking.entity.RankingSeason;
import com.ggukmoney.beanzip.domain.user.entity.AppUser;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WeeklyRankingRewardExpirationServiceTest {

    @Test
    void expiresEveryDuePendingRewardOnce() {
        Instant now = Instant.parse("2026-09-24T00:00:00Z");
        WeeklyRankingReward first = reward(now.minusSeconds(1), 10_000L);
        WeeklyRankingReward second = reward(now, 5_000L);
        WeeklyRankingRewardRepository repository = mock(WeeklyRankingRewardRepository.class);
        when(repository.findExpiredPendingForUpdate(now)).thenReturn(List.of(first, second));
        WeeklyRankingRewardExpirationService service = new WeeklyRankingRewardExpirationService(repository);

        assertThat(service.expireDue(now)).isEqualTo(2);
        assertThat(first.getStatus()).isEqualTo(WeeklyRankingReward.Status.EXPIRED);
        assertThat(second.getStatus()).isEqualTo(WeeklyRankingReward.Status.EXPIRED);
        assertThat(service.expireDue(now)).isZero();
    }

    private WeeklyRankingReward reward(Instant expiresAt, long amount) {
        return WeeklyRankingReward.open(
                mock(RankingSeason.class), mock(AppUser.class), 1L, 1, 100L, amount, expiresAt);
    }
}
