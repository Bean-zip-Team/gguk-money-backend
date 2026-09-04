package com.ggukmoney.beanzip.domain.ranking.service;

import com.ggukmoney.beanzip.domain.ranking.entity.RankingSeason;
import com.ggukmoney.beanzip.domain.ranking.event.RankingWeeklySeasonActivatedEvent;
import com.ggukmoney.beanzip.domain.ranking.repository.RankingSeasonRepository;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.lang.reflect.Method;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RankingWeeklySeasonActivatedListenerTest {

    private final RankingSeasonRepository seasonRepository = mock(RankingSeasonRepository.class);
    private final RankingBackfillService backfillService = mock(RankingBackfillService.class);
    private final RankingRebuildService rebuildService = mock(RankingRebuildService.class);
    private final RankingWeeklySeasonActivatedListener listener =
            new RankingWeeklySeasonActivatedListener(seasonRepository, backfillService, rebuildService);

    @Test
    void backfillsAndRebuildsActiveWeeklySeasonAfterCommit() {
        RankingSeason season = weeklySeason();
        ReflectionTestUtils.setField(season, "id", 10L);
        when(seasonRepository.findById(10L)).thenReturn(Optional.of(season));

        listener.handle(new RankingWeeklySeasonActivatedEvent(10L));

        InOrder inOrder = inOrder(backfillService, rebuildService);
        inOrder.verify(backfillService).backfillActiveWeeklySeason(season);
        inOrder.verify(rebuildService).rebuild(season, "weekly-season-activated");
    }

    @Test
    void runtimeFailureDoesNotPropagateOutsideListener() {
        RankingSeason season = weeklySeason();
        ReflectionTestUtils.setField(season, "id", 10L);
        when(seasonRepository.findById(10L)).thenReturn(Optional.of(season));
        doThrow(new IllegalStateException("backfill failed"))
                .when(backfillService)
                .backfillActiveWeeklySeason(season);

        assertThatCode(() -> listener.handle(new RankingWeeklySeasonActivatedEvent(10L)))
                .doesNotThrowAnyException();
    }

    @Test
    void handleIsAfterCommitTransactionalEventListener() throws NoSuchMethodException {
        Method method = RankingWeeklySeasonActivatedListener.class.getMethod(
                "handle",
                RankingWeeklySeasonActivatedEvent.class
        );

        TransactionalEventListener annotation = method.getAnnotation(TransactionalEventListener.class);

        assertThat(annotation).isNotNull();
        assertThat(annotation.phase()).isEqualTo(TransactionPhase.AFTER_COMMIT);
    }

    private RankingSeason weeklySeason() {
        return RankingSeason.activeWeekly(
                LocalDate.of(2026, 7, 20),
                Instant.parse("2026-07-19T15:00:00Z"),
                Instant.parse("2026-07-26T15:00:00Z")
        );
    }
}
