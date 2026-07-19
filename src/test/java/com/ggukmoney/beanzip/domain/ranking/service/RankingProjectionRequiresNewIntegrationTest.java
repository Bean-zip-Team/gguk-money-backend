package com.ggukmoney.beanzip.domain.ranking.service;

import com.ggukmoney.beanzip.domain.ranking.entity.RankingEntry;
import com.ggukmoney.beanzip.domain.ranking.entity.RankingSeason;
import com.ggukmoney.beanzip.domain.ranking.repository.RankingEntryRepository;
import com.ggukmoney.beanzip.domain.tap.entity.UserTapProgress;
import com.ggukmoney.beanzip.domain.tap.repository.UserTapProgressRepository;
import com.ggukmoney.beanzip.domain.user.entity.AppUser;
import com.ggukmoney.beanzip.domain.user.repository.AppUserRepository;
import com.ggukmoney.beanzip.support.FullStackIntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RankingProjectionRequiresNewIntegrationTest extends FullStackIntegrationTestSupport {

    @Autowired
    private RankingProjectionService rankingProjectionService;

    @Autowired
    private RankingSeasonService rankingSeasonService;

    @Autowired
    private RankingEntryRepository rankingEntryRepository;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private UserTapProgressRepository userTapProgressRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void syncLatestAllTimeScoreCommitsInRequiresNewEvenWhenOuterTransactionRollsBack() {
        AppUser user = appUserRepository.save(AppUser.createActive("ranking-requires-new", null));
        UserTapProgress progress = UserTapProgress.createFor(user, 1, 1);
        progress.addValidTaps(110L);
        userTapProgressRepository.save(progress);

        assertThatThrownBy(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            rankingProjectionService.syncLatestAllTimeScore(user.getId());
            throw new IllegalStateException("outer rollback");
        })).isInstanceOf(IllegalStateException.class);

        RankingSeason season = rankingSeasonService.getActiveAllTimeSeason();
        RankingEntry entry = rankingEntryRepository.findBySeasonAndUserId(season, user.getId()).orElseThrow();
        assertThat(entry.getScore()).isEqualTo(110L);
    }
}
