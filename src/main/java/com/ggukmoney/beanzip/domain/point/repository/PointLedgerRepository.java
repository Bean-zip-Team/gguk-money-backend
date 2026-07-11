package com.ggukmoney.beanzip.domain.point.repository;

import com.ggukmoney.beanzip.domain.point.entity.PointLedger;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface PointLedgerRepository extends JpaRepository<PointLedger, Long> {

    boolean existsByUserIdAndIdempotencyKey(UUID userId, UUID idempotencyKey);
}
