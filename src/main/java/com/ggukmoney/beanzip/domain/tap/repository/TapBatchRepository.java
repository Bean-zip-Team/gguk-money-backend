package com.ggukmoney.beanzip.domain.tap.repository;

import com.ggukmoney.beanzip.domain.tap.entity.TapBatch;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TapBatchRepository extends JpaRepository<TapBatch, Long> {
}
