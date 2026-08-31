package com.ggukmoney.beanzip.domain.tap.repository;

import com.ggukmoney.beanzip.domain.tap.entity.UserTapDaily;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

public interface UserTapDailyRepository extends JpaRepository<UserTapDaily, Long> {

    Optional<UserTapDaily> findByUserIdAndTapDate(UUID userId, LocalDate tapDate);

    boolean existsByUserIdAndTapDateAndValidTapCountGreaterThan(UUID userId, LocalDate tapDate, Integer validTapCount);

    @Query("""
            SELECT daily.user.id
            FROM UserTapDaily daily
            WHERE daily.user.id IN :userIds
              AND daily.tapDate = :tapDate
              AND daily.validTapCount > 0
            """)
    List<UUID> findUserIdsWithValidTaps(
            @Param("userIds") Collection<UUID> userIds,
            @Param("tapDate") LocalDate tapDate
    );

    @Query("""
            SELECT COALESCE(SUM(daily.totalValidTapCount), 0)
            FROM UserTapDaily daily
            WHERE daily.user.id = :userId
              AND daily.tapDate >= :startDate
              AND daily.tapDate < :endDate
            """)
    long sumTotalValidTapCount(
            @Param("userId") UUID userId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    @Query(value = """
            SELECT daily.user_id AS userId,
                   COALESCE(SUM(daily.total_valid_tap_count), 0) AS score
            FROM user_tap_daily daily
            WHERE daily.tap_date >= :startDate
              AND daily.tap_date < :endDate
              AND daily.total_valid_tap_count > 0
              AND (:lastUserId IS NULL OR CAST(daily.user_id AS text) > :lastUserId)
            GROUP BY daily.user_id
            ORDER BY CAST(daily.user_id AS text) ASC
            LIMIT :limit
            """, nativeQuery = true)
    List<UserTapAggregateProjection> findTotalValidTapAggregates(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("lastUserId") String lastUserId,
            @Param("limit") int limit
    );

    interface UserTapAggregateProjection {
        UUID getUserId();

        Long getScore();
    }
}
