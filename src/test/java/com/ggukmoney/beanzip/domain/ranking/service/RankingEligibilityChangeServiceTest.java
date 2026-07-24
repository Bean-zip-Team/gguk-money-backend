package com.ggukmoney.beanzip.domain.ranking.service;

import com.ggukmoney.beanzip.domain.ranking.entity.RankingEntry;
import com.ggukmoney.beanzip.domain.ranking.entity.RankingSeason;
import com.ggukmoney.beanzip.domain.ranking.event.RankingScoreChangedEvent;
import com.ggukmoney.beanzip.domain.ranking.repository.RankingEntryRepository;
import com.ggukmoney.beanzip.domain.user.entity.AppUser;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RankingEligibilityChangeServiceTest {

    private final RankingSeasonService seasonService = mock(RankingSeasonService.class);
    private final RankingEntryRepository entryRepository = mock(RankingEntryRepository.class);
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-07-19T01:00:00Z"), ZoneOffset.UTC);
    private final RankingEligibilityChangeService service = new RankingEligibilityChangeService(
            seasonService,
            entryRepository,
            eventPublisher,
            clock
    );

    @Test
    void publishesIneligibleEventWhenUserStatusNoLongerParticipates() {
        UUID userId = UUID.randomUUID();
        AppUser user = AppUser.createActive("me", null);
        ReflectionTestUtils.setField(user, "id", userId);
        RankingSeason season = weeklySeason();
        ReflectionTestUtils.setField(season, "id", 1L);
        RankingEntry entry = RankingEntry.createFor(season, user, 100L, null, Instant.parse("2026-07-19T00:00:00Z"));
        user.withdraw();
        when(seasonService.findActiveWeeklySeason()).thenReturn(Optional.of(season));
        when(entryRepository.findBySeasonAndUserId(season, userId)).thenReturn(Optional.of(entry));
        when(entryRepository.save(entry)).thenReturn(entry);

        service.publishAllTimeEligibilityChanged(user);

        ArgumentCaptor<RankingScoreChangedEvent> captor = ArgumentCaptor.forClass(RankingScoreChangedEvent.class);
        verify(entryRepository).save(entry);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().participantEligible()).isFalse();
        assertThat(captor.getValue().userId()).isEqualTo(userId);
        assertThat(entry.getScoreUpdatedAt()).isEqualTo(Instant.parse("2026-07-19T01:00:00Z"));
    }

    @Test
    void touchesEntryAndPublishesIneligibleEventWhenUserIsSuspended() {
        UUID userId = UUID.randomUUID();
        AppUser user = AppUser.createActive("me", null);
        ReflectionTestUtils.setField(user, "id", userId);
        RankingSeason season = weeklySeason();
        ReflectionTestUtils.setField(season, "id", 1L);
        RankingEntry entry = RankingEntry.createFor(season, user, 100L, null, Instant.parse("2026-07-19T00:00:00Z"));
        user.suspend();
        when(seasonService.findActiveWeeklySeason()).thenReturn(Optional.of(season));
        when(entryRepository.findBySeasonAndUserId(season, userId)).thenReturn(Optional.of(entry));
        when(entryRepository.save(entry)).thenReturn(entry);

        service.publishAllTimeEligibilityChanged(user);

        ArgumentCaptor<RankingScoreChangedEvent> captor = ArgumentCaptor.forClass(RankingScoreChangedEvent.class);
        verify(entryRepository).save(entry);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().participantEligible()).isFalse();
        assertThat(entry.getScoreUpdatedAt()).isEqualTo(Instant.parse("2026-07-19T01:00:00Z"));
    }

    @Test
    void handlesStatusChangeForUserWithoutRankingEntryAsNoOp() {
        UUID userId = UUID.randomUUID();
        AppUser user = AppUser.createActive("me", null);
        ReflectionTestUtils.setField(user, "id", userId);
        RankingSeason season = weeklySeason();
        ReflectionTestUtils.setField(season, "id", 1L);
        user.withdraw();
        when(seasonService.findActiveWeeklySeason()).thenReturn(Optional.of(season));
        when(entryRepository.findBySeasonAndUserId(season, userId)).thenReturn(Optional.empty());

        service.publishAllTimeEligibilityChanged(user);

        verify(entryRepository, never()).save(org.mockito.ArgumentMatchers.any());
        verify(eventPublisher, never()).publishEvent(org.mockito.ArgumentMatchers.any());
    }

    private RankingSeason weeklySeason() {
        return RankingSeason.activeWeekly(
                LocalDate.of(2026, 7, 20),
                Instant.parse("2026-07-19T15:00:00Z"),
                Instant.parse("2026-07-26T15:00:00Z")
        );
    }
}
