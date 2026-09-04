package com.ggukmoney.beanzip.domain.ranking.service;

import com.ggukmoney.beanzip.domain.ranking.entity.RankingSeason;
import com.ggukmoney.beanzip.domain.ranking.entity.RankingSeasonStatus;
import com.ggukmoney.beanzip.domain.ranking.event.RankingWeeklySeasonActivatedEvent;
import com.ggukmoney.beanzip.domain.ranking.repository.RankingSeasonRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class RankingWeeklySeasonActivatedListener {

    private static final Logger log = LoggerFactory.getLogger(RankingWeeklySeasonActivatedListener.class);

    private final RankingSeasonRepository seasonRepository;
    private final RankingBackfillService backfillService;
    private final RankingRebuildService rebuildService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(RankingWeeklySeasonActivatedEvent event) {
        try {
            seasonRepository.findById(event.seasonId())
                    .filter(RankingSeason::isWeekly)
                    .filter(season -> season.getStatus() == RankingSeasonStatus.ACTIVE)
                    .ifPresent(this::backfillAndRebuild);
        } catch (RuntimeException exception) {
            log.error("Failed to initialize active weekly ranking seasonId={}", event.seasonId(), exception);
        }
    }

    private void backfillAndRebuild(RankingSeason season) {
        backfillService.backfillActiveWeeklySeason(season);
        rebuildService.rebuild(season, "weekly-season-activated");
    }
}
