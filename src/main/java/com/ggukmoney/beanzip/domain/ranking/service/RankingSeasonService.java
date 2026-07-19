package com.ggukmoney.beanzip.domain.ranking.service;

import com.ggukmoney.beanzip.domain.ranking.entity.RankingSeason;
import com.ggukmoney.beanzip.domain.ranking.entity.RankingSeasonStatus;
import com.ggukmoney.beanzip.domain.ranking.repository.RankingSeasonRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class RankingSeasonService {

    private final RankingSeasonRepository seasonRepository;
    private final PlatformTransactionManager transactionManager;
    private final Clock clock;

    public Optional<RankingSeason> findActiveAllTimeSeason() {
        return seasonRepository.findByCodeAndStatus(RankingSeason.ALL_TIME_CODE, RankingSeasonStatus.ACTIVE);
    }

    public RankingSeason getActiveAllTimeSeason() {
        return findActiveAllTimeSeason()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "RANKING_SEASON_NOT_FOUND"));
    }

    public RankingSeason getOrCreateActiveAllTimeSeason() {
        Optional<RankingSeason> existing = findActiveAllTimeSeason();
        if (existing.isPresent()) {
            return existing.get();
        }
        try {
            return createActiveAllTimeSeasonInNewTransaction();
        } catch (DataIntegrityViolationException exception) {
            return findActiveAllTimeSeason().orElseThrow(() -> exception);
        }
    }

    private RankingSeason createActiveAllTimeSeasonInNewTransaction() {
        DefaultTransactionDefinition definition = new DefaultTransactionDefinition();
        definition.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        TransactionStatus status = transactionManager.getTransaction(definition);
        try {
            RankingSeason season = seasonRepository.saveAndFlush(RankingSeason.activeAllTime(clock.instant()));
            transactionManager.commit(status);
            return season;
        } catch (RuntimeException exception) {
            transactionManager.rollback(status);
            throw exception;
        }
    }
}
