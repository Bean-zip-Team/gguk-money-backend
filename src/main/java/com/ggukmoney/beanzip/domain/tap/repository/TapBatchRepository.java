package com.ggukmoney.beanzip.domain.tap.repository;

import com.ggukmoney.beanzip.domain.tap.entity.TapBatch;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface TapBatchRepository extends JpaRepository<TapBatch, Long> {

    Optional<TapBatch> findByUserIdAndTapSessionIdAndSequence(UUID userId, UUID tapSessionId, Long sequence);
}
