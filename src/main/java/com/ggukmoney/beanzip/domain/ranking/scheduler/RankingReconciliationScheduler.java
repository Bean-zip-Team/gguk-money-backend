package com.ggukmoney.beanzip.domain.ranking.scheduler;

import com.ggukmoney.beanzip.domain.ranking.service.RankingReconciliationService;
import com.ggukmoney.beanzip.domain.ranking.service.RankingProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RankingReconciliationScheduler {

    private final RankingReconciliationService reconciliationService;
    private final RankingProperties rankingProperties;

    @Scheduled(fixedDelayString = "#{@rankingProperties.reconciliationInterval().toMillis()}")
    public void reconcileActiveAllTime() {
        reconciliationService.reconcileActiveAllTime();
    }
}
