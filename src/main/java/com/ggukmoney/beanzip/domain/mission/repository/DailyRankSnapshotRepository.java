package com.ggukmoney.beanzip.domain.mission.repository;

import com.ggukmoney.beanzip.domain.mission.entity.DailyRankSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

public interface DailyRankSnapshotRepository extends JpaRepository<DailyRankSnapshot, Long> {

    Optional<DailyRankSnapshot> findByUserIdAndSnapshotDate(UUID userId, LocalDate snapshotDate);

    /**
     * 그 시각의 순위를 한 문장으로 찍는다.
     *
     * <p>순위 계산 방식은 랭킹 도메인과 같아야 한다 — 점수 내림차순, 동점이면 {@code user_id} 를
     * 문자열로 비교해 내림차순이다({@code countParticipantsAhead}, {@code findTopParticipants} 와 동일).
     * 스냅샷과 현재 순위를 서로 다른 기준으로 매기면 동점 구간에서 상승폭이 실제와 어긋난다.
     *
     * <p>같은 날 다시 돌면 값을 덮어쓴다. 배치가 재시작되거나 수동으로 다시 돌려도 결과가 같다.
     * 충돌 대상은 제약 이름이 아니라 컬럼으로 지정한다 — 운영 DDL 은 유니크 인덱스를 만들 뿐
     * 제약을 만들지 않아서, 이름으로 지정하면 운영에서만 실패한다.
     *
     * @return 찍은 행 수
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            INSERT INTO daily_rank_snapshot (user_id, snapshot_date, season_id, rank_value, created_at)
            SELECT ranked.user_id, :snapshotDate, :seasonId, ranked.calculated_rank, :now
            FROM (
                SELECT e.user_id,
                       ROW_NUMBER() OVER (ORDER BY e.score DESC, CAST(e.user_id AS text) DESC) AS calculated_rank
                FROM ranking_entry e
                JOIN app_user u ON u.id = e.user_id
                WHERE e.season_id = :seasonId
                  AND u.status = 'ACTIVE'
                  AND e.score > 0
            ) ranked
            ON CONFLICT (user_id, snapshot_date) DO UPDATE
            SET season_id = EXCLUDED.season_id,
                rank_value = EXCLUDED.rank_value,
                created_at = EXCLUDED.created_at
            """, nativeQuery = true)
    int capture(
            @Param("seasonId") Long seasonId,
            @Param("snapshotDate") LocalDate snapshotDate,
            @Param("now") Instant now
    );

    /**
     * 그날 찍어 둔 스냅샷을 지운다.
     *
     * <p>다시 찍기 전에 먼저 비운다. {@code ON CONFLICT} 갱신만으로는 그 사이 탈퇴했거나 점수가
     * 0이 된 유저의 옛 순위가 남아, 한 날짜 안에 두 세대의 순위가 섞인다.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from DailyRankSnapshot snapshot where snapshot.snapshotDate = :snapshotDate")
    int deleteBySnapshotDate(@Param("snapshotDate") LocalDate snapshotDate);

    /** 오래된 스냅샷 정리. 판정은 어제 것만 보므로 며칠치만 남겨 둔다. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from DailyRankSnapshot snapshot where snapshot.snapshotDate < :oldestKeptDate")
    int deleteOlderThan(@Param("oldestKeptDate") LocalDate oldestKeptDate);
}
