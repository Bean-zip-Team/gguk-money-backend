package com.ggukmoney.beanzip.domain.ranking.service;

import com.ggukmoney.beanzip.domain.ranking.event.RankingScoreSyncRequestedEvent;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class RankingScoreSyncRequestedListenerTest {

    private final RankingProjectionService projectionService = mock(RankingProjectionService.class);
    private final RankingScoreSyncRequestedListener listener = new RankingScoreSyncRequestedListener(projectionService);
    private final Instant occurredAt = Instant.parse("2026-07-20T15:00:00Z");

    @Test
    void synchronizesLatestScoreAfterCommit() {
        UUID userId = UUID.randomUUID();

        listener.handle(new RankingScoreSyncRequestedEvent(userId, occurredAt));

        verify(projectionService).syncLatestWeeklyScore(userId, occurredAt);
    }

    @Test
    void projectionFailureDoesNotPropagateOutsideListener() {
        UUID userId = UUID.randomUUID();
        doThrow(new IllegalStateException("projection failed"))
                .when(projectionService)
                .syncLatestWeeklyScore(userId, occurredAt);

        assertThatCode(() -> listener.handle(new RankingScoreSyncRequestedEvent(userId, occurredAt)))
                .doesNotThrowAnyException();
    }

    @Test
    void handleIsAfterCommitTransactionalEventListener() throws NoSuchMethodException {
        Method method = RankingScoreSyncRequestedListener.class.getMethod("handle", RankingScoreSyncRequestedEvent.class);

        TransactionalEventListener annotation = method.getAnnotation(TransactionalEventListener.class);

        assertThat(annotation).isNotNull();
        assertThat(annotation.phase()).isEqualTo(TransactionPhase.AFTER_COMMIT);
    }
}
