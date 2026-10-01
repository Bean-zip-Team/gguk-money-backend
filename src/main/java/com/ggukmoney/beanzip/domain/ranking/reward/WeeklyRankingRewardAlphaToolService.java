package com.ggukmoney.beanzip.domain.ranking.reward;

import com.ggukmoney.beanzip.domain.ranking.entity.RankingSeason;
import com.ggukmoney.beanzip.domain.ranking.repository.RankingSeasonRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

@Service
@Profile("alpha")
@ConditionalOnProperty(name = "app.ranking.weekly-reward.alpha-tools-enabled", havingValue = "true")
@RequiredArgsConstructor
public class WeeklyRankingRewardAlphaToolService {

    private final RankingSeasonRepository seasonRepository;
    private final WeeklyRankingRewardRepository rewardRepository;
    private final WeeklyRankingRewardSnapshotService snapshotService;
    private final Clock clock;

    @Transactional
    public void reset(String seasonCode) {
        rewardRepository.deleteBySeasonId(season(seasonCode).getId());
    }

    @Transactional
    public int snapshot(String seasonCode) {
        RankingSeason season = season(seasonCode);
        return snapshotService.snapshot(season, clock.instant());
    }

    private RankingSeason season(String seasonCode) {
        return seasonRepository.findByCode(seasonCode)
                .orElseThrow(() -> new IllegalArgumentException("ranking season not found code=" + seasonCode));
    }
}
