package com.ggukmoney.beanzip.domain.tap.repository;

import com.ggukmoney.beanzip.domain.tap.entity.UserTapDaily;
import org.springframework.data.domain.Pageable;
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

    /**
     * 최근 기록이 있는 날짜를 최신순으로. 연속 출석 일수를 세는 데 쓴다(BEA-299).
     *
     * <p>행이 만들어지는 시점이 곧 앱 진입이라 이 날짜 목록이 출석 기록이 된다.
     *
     * <p>{@code Pageable} 은 <b>건수 제한 용도로만</b> 넘긴다. 정렬을 실어 보내면 JPQL 의
     * {@code ORDER BY} 뒤에 덧붙어 의도와 다른 순서가 된다.
     */
    @Query("""
            SELECT daily.tapDate
            FROM UserTapDaily daily
            WHERE daily.user.id = :userId
              AND daily.tapDate <= :today
            ORDER BY daily.tapDate DESC
            """)
    List<LocalDate> findRecentTapDates(
            @Param("userId") UUID userId,
            @Param("today") LocalDate today,
            Pageable pageable
    );

    @Query("""
            SELECT COALESCE(SUM(daily.totalEffectiveTapCount), 0)
            FROM UserTapDaily daily
            WHERE daily.user.id = :userId
              AND daily.tapDate >= :startDate
              AND daily.tapDate < :endDate
            """)
    long sumTotalEffectiveTapCount(
            @Param("userId") UUID userId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    @Query(value = """
            SELECT daily.user_id AS userId,
                   COALESCE(SUM(daily.total_effective_tap_count), 0) AS score
            FROM user_tap_daily daily
            WHERE daily.tap_date >= :startDate
              AND daily.tap_date < :endDate
              AND daily.total_effective_tap_count > 0
              AND (:lastUserId IS NULL OR daily.user_id > :lastUserId)
            GROUP BY daily.user_id
            ORDER BY daily.user_id ASC
            LIMIT :limit
            """, nativeQuery = true)
    List<UserTapAggregateProjection> findTotalEffectiveTapAggregates(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("lastUserId") UUID lastUserId,
            @Param("limit") int limit
    );

    /** 이 날짜에 여러 사용자가 누른 수(자동 클릭 포함). 그날 기록이 없는 사용자는 결과에 없다 (BEA-390). */
    @Query("""
            SELECT daily.user.id AS userId, daily.totalEffectiveTapCount AS tapCount
            FROM UserTapDaily daily
            WHERE daily.user.id IN :userIds
              AND daily.tapDate = :tapDate
            """)
    List<UserTapCountProjection> findEffectiveTapCounts(
            @Param("userIds") Collection<UUID> userIds,
            @Param("tapDate") LocalDate tapDate
    );

    /**
     * 여러 사용자가 마지막으로 누른 날. 한 번도 누르지 않은 사용자는 결과에 없다 (BEA-390).
     *
     * <p>행은 앱에 들어오기만 해도 생기므로 누른 수가 있는 날만 센다.
     */
    @Query("""
            SELECT daily.user.id AS userId, MAX(daily.tapDate) AS lastTapDate
            FROM UserTapDaily daily
            WHERE daily.user.id IN :userIds
              AND daily.totalEffectiveTapCount > 0
            GROUP BY daily.user.id
            """)
    List<UserLastTapDateProjection> findLastEffectiveTapDates(@Param("userIds") Collection<UUID> userIds);

    interface UserTapCountProjection {
        UUID getUserId();

        Long getTapCount();
    }

    interface UserLastTapDateProjection {
        UUID getUserId();

        LocalDate getLastTapDate();
    }

    interface UserTapAggregateProjection {
        UUID getUserId();

        Long getScore();
    }
}
