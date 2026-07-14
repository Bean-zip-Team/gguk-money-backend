package com.ggukmoney.beanzip.domain.cashout.repository;

import com.ggukmoney.beanzip.domain.cashout.entity.CashoutRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

public interface CashoutRequestRepository extends JpaRepository<CashoutRequest, Long> {

    Optional<CashoutRequest> findByUserIdAndIdempotencyKey(UUID userId, UUID idempotencyKey);

    boolean existsByUserIdAndStatusIn(UUID userId, Collection<CashoutRequest.Status> statuses);
}
