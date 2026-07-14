package com.ggukmoney.beanzip.domain.point.repository;

import com.ggukmoney.beanzip.domain.point.entity.PointLedger;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.UUID;

public interface PointLedgerRepository extends JpaRepository<PointLedger, Long>, JpaSpecificationExecutor<PointLedger> {

    boolean existsByUserIdAndIdempotencyKey(UUID userId, UUID idempotencyKey);
}
