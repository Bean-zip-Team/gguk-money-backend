package com.ggukmoney.beanzip.domain.ranking.service;

import com.ggukmoney.beanzip.domain.ranking.entity.RankingSeason;
import com.ggukmoney.beanzip.domain.ranking.entity.RankingSeasonStatus;
import com.ggukmoney.beanzip.domain.ranking.repository.RankingSeasonRepository;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RankingSeasonServiceTest {

    private final RankingSeasonRepository seasonRepository = mock(RankingSeasonRepository.class);
    private final PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
    private final TransactionStatus transactionStatus = mock(TransactionStatus.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-07-19T01:00:00Z"), ZoneOffset.UTC);
    private final RankingSeasonService service = new RankingSeasonService(seasonRepository, transactionManager, clock);

    @Test
    void findsOnlyActiveAllTimeSeason() {
        RankingSeason active = RankingSeason.activeAllTime(Instant.parse("2026-07-19T00:00:00Z"));
        when(seasonRepository.findByCodeAndStatus("ALL_TIME", RankingSeasonStatus.ACTIVE))
                .thenReturn(Optional.of(active));

        assertThat(service.findActiveAllTimeSeason()).containsSame(active);
    }

    @Test
    void doesNotTreatClosedAllTimeSeasonAsCurrent() {
        when(seasonRepository.findByCodeAndStatus("ALL_TIME", RankingSeasonStatus.ACTIVE))
                .thenReturn(Optional.empty());

        assertThatThrownBy(service::getActiveAllTimeSeason)
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
                .hasMessageContaining("RANKING_SEASON_NOT_FOUND");
    }

    @Test
    void createsSeasonInRequiresNewTransactionAndRequeriesAfterUniqueConflict() {
        RankingSeason active = RankingSeason.activeAllTime(Instant.parse("2026-07-19T00:00:00Z"));
        when(transactionManager.getTransaction(any())).thenReturn(transactionStatus);
        when(seasonRepository.findByCodeAndStatus("ALL_TIME", RankingSeasonStatus.ACTIVE))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(active));
        when(seasonRepository.saveAndFlush(any(RankingSeason.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate"));

        RankingSeason result = service.getOrCreateActiveAllTimeSeason();

        assertThat(result).isSameAs(active);
        verify(transactionManager).getTransaction(argThat(definition ->
                definition.getPropagationBehavior() == TransactionDefinition.PROPAGATION_REQUIRES_NEW
        ));
        verify(transactionManager).rollback(transactionStatus);
    }
}
