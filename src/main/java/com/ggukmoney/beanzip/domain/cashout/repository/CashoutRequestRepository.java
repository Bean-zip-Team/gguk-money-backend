package com.ggukmoney.beanzip.domain.cashout.repository;

import com.ggukmoney.beanzip.domain.cashout.entity.CashoutRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

public interface CashoutRequestRepository extends JpaRepository<CashoutRequest, Long>, JpaSpecificationExecutor<CashoutRequest> {

    Optional<CashoutRequest> findByUserIdAndIdempotencyKey(UUID userId, UUID idempotencyKey);

    boolean existsByUserIdAndStatusIn(UUID userId, Collection<CashoutRequest.Status> statuses);

    Optional<CashoutRequest> findByPublicIdAndUserId(UUID publicId, UUID userId);
}
