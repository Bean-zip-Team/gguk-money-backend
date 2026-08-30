package com.ggukmoney.beanzip.domain.promotion.repository;

import com.ggukmoney.beanzip.domain.promotion.entity.PromotionGrant;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.QueryHint;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PromotionGrantRepository extends JpaRepository<PromotionGrant, Long> {

    boolean existsByUserIdAndPromotionCode(UUID userId, String promotionCode);

    Optional<PromotionGrant> findByPublicId(UUID publicId);

    /**
     * 처리할 차례가 된 행을 배치 상한만큼 잠그고 가져온다.
     *
     * <p>{@code hold_reason IS NULL} 이 술어에 들어가는 게 중요하다. key 커밋에 실패해 보류해둔
     * 행이 다시 집히면 새 key 를 발급받게 되고, 그건 이중지급 경로다.
     *
     * <p>lock timeout -2 는 Hibernate 의 SKIP LOCKED 다. 블루/그린 전환 중 두 인스턴스가
     * 겹쳐 도는 구간에서 같은 행을 두 번 집지 않게 한다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    @Query("""
            select grant
            from PromotionGrant grant
            where grant.status in (
                      com.ggukmoney.beanzip.domain.promotion.entity.PromotionGrant.Status.PENDING,
                      com.ggukmoney.beanzip.domain.promotion.entity.PromotionGrant.Status.PROCESSING)
              and grant.holdReason is null
              and grant.nextAttemptAt <= :now
            order by grant.nextAttemptAt asc, grant.createdAt asc
            """)
    List<PromotionGrant> findDueForUpdate(@Param("now") Instant now, Pageable pageable);

    @Query("""
            select grant.status as status, count(grant) as count
            from PromotionGrant grant
            where grant.promotionCode = :promotionCode
            group by grant.status
            """)
    List<StatusCount> countByStatus(@Param("promotionCode") String promotionCode);

    long countByPromotionCodeAndNeedsReviewTrue(String promotionCode);

    long countByPromotionCodeAndHoldReasonIsNotNull(String promotionCode);

    @Query("""
            select min(grant.createdAt)
            from PromotionGrant grant
            where grant.promotionCode = :promotionCode
              and grant.status in (
                      com.ggukmoney.beanzip.domain.promotion.entity.PromotionGrant.Status.PENDING,
                      com.ggukmoney.beanzip.domain.promotion.entity.PromotionGrant.Status.PROCESSING)
            """)
    Optional<Instant> findOldestUnsettledCreatedAt(@Param("promotionCode") String promotionCode);

    interface StatusCount {
        PromotionGrant.Status getStatus();

        long getCount();
    }
}
