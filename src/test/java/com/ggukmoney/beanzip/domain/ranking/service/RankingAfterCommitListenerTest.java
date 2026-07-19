package com.ggukmoney.beanzip.domain.ranking.service;

import com.ggukmoney.beanzip.domain.ranking.event.RankingScoreChangedEvent;
import com.ggukmoney.beanzip.domain.ranking.redis.RankingRedisRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class RankingAfterCommitListenerTest {

    private final RankingRedisRepository redisRepository = mock(RankingRedisRepository.class);
    private final RankingAfterCommitListener listener = new RankingAfterCommitListener(redisRepository);

    @Test
    void eligibleParticipantUpdatesRedisProjectionAfterCommit() {
        UUID userId = UUID.randomUUID();
        RankingScoreChangedEvent event = new RankingScoreChangedEvent(
                1L,
                userId,
                100L,
                null,
                null,
                true,
                Instant.parse("2026-07-19T00:00:00Z")
        );

        listener.handle(event);

        verify(redisRepository).updateScore(1L, userId, 100L, null, null);
    }

    @Test
    void redisFailureDoesNotPropagateAfterCommit() {
        UUID userId = UUID.randomUUID();
        doThrow(new IllegalStateException("redis down"))
                .when(redisRepository)
                .updateScore(1L, userId, 100L, null, null);
        RankingScoreChangedEvent event = new RankingScoreChangedEvent(
                1L,
                userId,
                100L,
                null,
                null,
                true,
                Instant.parse("2026-07-19T00:00:00Z")
        );

        assertThatCode(() -> listener.handle(event)).doesNotThrowAnyException();
    }
}
