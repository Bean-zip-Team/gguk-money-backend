package com.ggukmoney.beanzip.domain.ranking.service;

import com.ggukmoney.beanzip.domain.ranking.entity.RankingEntry;
import com.ggukmoney.beanzip.domain.ranking.entity.RankingSeason;
import com.ggukmoney.beanzip.domain.ranking.event.RankingScoreChangedEvent;
import com.ggukmoney.beanzip.domain.ranking.repository.RankingEntryRepository;
import com.ggukmoney.beanzip.domain.user.entity.AppUser;
import com.ggukmoney.beanzip.domain.user.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RankingProjectionServiceTest {

    private final RankingSeasonService seasonService = mock(RankingSeasonService.class);
    private final RankingEntryRepository entryRepository = mock(RankingEntryRepository.class);
    private final UserService userService = mock(UserService.class);
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-07-19T01:00:00Z"), ZoneOffset.UTC);
    private final RankingProjectionService service =
            new RankingProjectionService(seasonService, entryRepository, userService, eventPublisher, clock);

    @Test
    void upsertsRankingEntryWithCumulativeTapCountAndPublishesAfterCommitEvent() {
        UUID userId = UUID.randomUUID();
        AppUser user = mock(AppUser.class);
        when(user.getId()).thenReturn(userId);
        when(user.getStatus()).thenReturn(AppUser.Status.ACTIVE);
        RankingSeason season = RankingSeason.activeAllTime(Instant.parse("2026-07-19T00:00:00Z"));
        when(seasonService.getOrCreateActiveAllTimeSeason()).thenReturn(season);
        when(userService.getById(userId)).thenReturn(user);
        when(entryRepository.findBySeasonAndUserId(season, userId)).thenReturn(Optional.empty());
        when(entryRepository.save(any(RankingEntry.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RankingEntry entry = service.syncAllTimeScore(userId, 123L);

        assertThat(entry.getScore()).isEqualTo(123L);
        verify(eventPublisher).publishEvent(any(RankingScoreChangedEvent.class));
    }

    @Test
    void reusesExistingEntryAndSetsScoreInsteadOfIncrementing() {
        UUID userId = UUID.randomUUID();
        AppUser user = mock(AppUser.class);
        when(user.getId()).thenReturn(userId);
        when(user.getStatus()).thenReturn(AppUser.Status.ACTIVE);
        RankingSeason season = RankingSeason.activeAllTime(Instant.parse("2026-07-19T00:00:00Z"));
        RankingEntry entry = RankingEntry.createFor(season, user, 100L, null, Instant.parse("2026-07-19T00:00:01Z"));
        when(seasonService.getOrCreateActiveAllTimeSeason()).thenReturn(season);
        when(userService.getById(userId)).thenReturn(user);
        when(entryRepository.findBySeasonAndUserId(season, userId)).thenReturn(Optional.of(entry));
        when(entryRepository.save(entry)).thenReturn(entry);

        RankingEntry result = service.syncAllTimeScore(userId, 120L);

        assertThat(result.getScore()).isEqualTo(120L);
    }
}
