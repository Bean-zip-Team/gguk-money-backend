package com.ggukmoney.beanzip.domain.tap.service;

import com.ggukmoney.beanzip.domain.tap.entity.TapBatch;
import com.ggukmoney.beanzip.domain.tap.repository.TapBatchRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TapBatchService {

    private final TapBatchRepository tapBatchRepository;

    public Optional<TapBatch> findExisting(UUID userId, UUID tapSessionId, Long sequence) {
        return tapBatchRepository.findByUserIdAndTapSessionIdAndSequence(userId, tapSessionId, sequence);
    }

    public List<TapBatch> findRecentForBotCheck(UUID userId, int sampleSize) {
        return tapBatchRepository.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(0, sampleSize));
    }

    public TapBatch save(TapBatch batch) {
        return tapBatchRepository.save(batch);
    }
}
