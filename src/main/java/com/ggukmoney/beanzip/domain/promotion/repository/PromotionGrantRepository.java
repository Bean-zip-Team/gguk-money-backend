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

    /** 실제로 나간 금액. 비즈 월렛 소진액과 대조한다. */
    @Query("""
            select coalesce(sum(grant.amount), 0)
            from PromotionGrant grant
            where grant.promotionCode = :promotionCode
              and grant.status = com.ggukmoney.beanzip.domain.promotion.entity.PromotionGrant.Status.SUCCEEDED
            """)
    long sumGrantedAmount(@Param("promotionCode") String promotionCode);

    /**
     * 에러코드 분포. 4112(머니 부족) 를 여기서 인지한다.
     *
     * <p>현재 상태 기준이라 코드마다 마지막 값만 잡힌다. 4112 는 해소될 때까지 유지되므로
     * 예산 감지에는 충분하지만, "몇 번 났었나" 같은 회고는 안 된다.
     */
    @Query("""
            select grant.tossErrorCode as code, count(grant) as count
            from PromotionGrant grant
            where grant.promotionCode = :promotionCode
              and grant.tossErrorCode is not null
            group by grant.tossErrorCode
            """)
    List<ErrorCodeCount> countByErrorCode(@Param("promotionCode") String promotionCode);

    List<PromotionGrant> findByPromotionCodeOrderByCreatedAtDesc(String promotionCode, Pageable pageable);

    /** CS 대응용 지목 조회. 1인 1회라 프로모션 수만큼만 나온다. */
    List<PromotionGrant> findByUserIdOrderByCreatedAtDesc(UUID userId);

    interface ErrorCodeCount {
        String getCode();

        long getCount();
    }

    interface StatusCount {
        PromotionGrant.Status getStatus();

        long getCount();
    }
}
