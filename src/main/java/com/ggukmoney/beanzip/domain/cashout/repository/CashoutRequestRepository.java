package com.ggukmoney.beanzip.domain.cashout.repository;

import com.ggukmoney.beanzip.domain.cashout.entity.CashoutRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CashoutRequestRepository extends JpaRepository<CashoutRequest, Long>, JpaSpecificationExecutor<CashoutRequest> {

    Optional<CashoutRequest> findByUserIdAndIdempotencyKey(UUID userId, UUID idempotencyKey);

    boolean existsByUserIdAndStatusIn(UUID userId, Collection<CashoutRequest.Status> statuses);

    Optional<CashoutRequest> findByPublicIdAndUserId(UUID publicId, UUID userId);

    @Query("""
            select request
            from CashoutRequest request
            join fetch request.user
            where request.status = :status
              and request.tossPromotionKey is not null
              and request.id > :lastProcessedId
            order by request.id asc
            """)
    List<CashoutRequest> findProcessingAfterId(
            @Param("status") CashoutRequest.Status status,
            @Param("lastProcessedId") long lastProcessedId,
            Pageable pageable
    );
}
