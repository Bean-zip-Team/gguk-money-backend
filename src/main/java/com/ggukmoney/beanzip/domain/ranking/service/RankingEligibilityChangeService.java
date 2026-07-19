package com.ggukmoney.beanzip.domain.ranking.service;

import com.ggukmoney.beanzip.domain.ranking.entity.RankingEntry;
import com.ggukmoney.beanzip.domain.ranking.entity.RankingSeason;
import com.ggukmoney.beanzip.domain.ranking.event.RankingScoreChangedEvent;
import com.ggukmoney.beanzip.domain.ranking.repository.RankingEntryRepository;
import com.ggukmoney.beanzip.domain.user.entity.AppUser;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

@Service
@RequiredArgsConstructor
public class RankingEligibilityChangeService {

    private final RankingSeasonService seasonService;
    private final RankingEntryRepository entryRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    @Transactional
    public void publishAllTimeEligibilityChanged(AppUser user) {
        seasonService.findActiveAllTimeSeason()
                .flatMap(season -> entryRepository.findBySeasonAndUserId(season, user.getId()))
                .ifPresent(this::publish);
    }

    private void publish(RankingEntry entry) {
        entry.touchForEligibilityChange(clock.instant());
        RankingEntry saved = entryRepository.save(entry);
        eventPublisher.publishEvent(new RankingScoreChangedEvent(
                saved.getSeason().getId(),
                saved.getUser().getId(),
                saved.getScore(),
                saved.getRegionCode(),
                saved.getRegionCode(),
                saved.isParticipantEligible(),
                clock.instant()
        ));
    }
}
