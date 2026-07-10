package com.ggukmoney.beanzip.domain.tap.service;

import com.ggukmoney.beanzip.domain.tap.entity.TapBatch;
import com.ggukmoney.beanzip.domain.tap.repository.TapBatchRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TapBatchServiceTest {

    private final TapBatchRepository tapBatchRepository = mock(TapBatchRepository.class);
    private final TapBatchService tapBatchService = new TapBatchService(tapBatchRepository);

    @Test
    void findExistingDelegatesToRepository() {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        TapBatch batch = mock(TapBatch.class);
        when(tapBatchRepository.findByUserIdAndTapSessionIdAndSequence(userId, sessionId, 1L))
                .thenReturn(Optional.of(batch));

        Optional<TapBatch> result = tapBatchService.findExisting(userId, sessionId, 1L);

        assertThat(result).contains(batch);
    }

    @Test
    void findRecentForBotCheckDelegatesToRepositoryWithPageRequest() {
        UUID userId = UUID.randomUUID();
        List<TapBatch> batches = List.of(mock(TapBatch.class));
        when(tapBatchRepository.findByUserIdOrderByCreatedAtDesc(eq(userId), any())).thenReturn(batches);

        List<TapBatch> result = tapBatchService.findRecentForBotCheck(userId, 10);

        assertThat(result).isEqualTo(batches);
    }

    @Test
    void saveDelegatesToRepository() {
        TapBatch batch = mock(TapBatch.class);
        when(tapBatchRepository.save(batch)).thenReturn(batch);

        TapBatch saved = tapBatchService.save(batch);

        assertThat(saved).isEqualTo(batch);
        verify(tapBatchRepository).save(batch);
    }
}
