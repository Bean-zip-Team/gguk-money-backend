package com.ggukmoney.beanzip.domain.booster.repository;

import com.ggukmoney.beanzip.domain.booster.entity.BoosterGrant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

public interface BoosterGrantRepository extends JpaRepository<BoosterGrant, Long> {

    Optional<BoosterGrant> findByUserIdAndStatusAndExpiresAtAfter(UUID userId, BoosterGrant.Status status, Instant now);

    long countByUserIdAndGrantDate(UUID userId, LocalDate grantDate);

    long countByUserIdAndStartsAtAfter(UUID userId, Instant startsAtAfter);

    @Query("""
            SELECT grant.user.id
            FROM BoosterGrant grant
            WHERE grant.grantDate = :grantDate
            GROUP BY grant.user.id
            HAVING COUNT(grant) >= :dailyLimit
            """)
    List<UUID> findUserIdsWhoExhaustedDailyBoosters(
            @Param("grantDate") LocalDate grantDate,
            @Param("dailyLimit") int dailyLimit
    );

    @Query("""
            SELECT grant.user.id AS userId,
                   COUNT(grant) AS grantCount
            FROM BoosterGrant grant
            WHERE grant.user.id IN :userIds
              AND grant.grantDate = :grantDate
            GROUP BY grant.user.id
            """)
    List<UserGrantCountProjection> countByUserIdsAndGrantDate(
            @Param("userIds") Collection<UUID> userIds,
            @Param("grantDate") LocalDate grantDate
    );

    interface UserGrantCountProjection {
        UUID getUserId();

        Long getGrantCount();
    }
}
