package com.ggukmoney.beanzip.domain.ranking.service;

import com.ggukmoney.beanzip.domain.ranking.event.RankingScoreSyncRequestedEvent;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.lang.reflect.Method;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class RankingScoreSyncRequestedListenerTest {

    private final RankingProjectionService projectionService = mock(RankingProjectionService.class);
    private final RankingScoreSyncRequestedListener listener = new RankingScoreSyncRequestedListener(projectionService);

    @Test
    void synchronizesLatestScoreAfterCommit() {
        UUID userId = UUID.randomUUID();

        listener.handle(new RankingScoreSyncRequestedEvent(userId));

        verify(projectionService).syncLatestAllTimeScore(userId);
    }

    @Test
    void projectionFailureDoesNotPropagateOutsideListener() {
        UUID userId = UUID.randomUUID();
        doThrow(new IllegalStateException("projection failed"))
                .when(projectionService)
                .syncLatestAllTimeScore(userId);

        assertThatCode(() -> listener.handle(new RankingScoreSyncRequestedEvent(userId)))
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
