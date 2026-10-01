package com.ggukmoney.beanzip.domain.mission.service;

import com.ggukmoney.beanzip.domain.mission.repository.DailyRankSnapshotRepository;
import com.ggukmoney.beanzip.domain.ranking.entity.RankingSeason;
import com.ggukmoney.beanzip.domain.ranking.service.RankingSeasonService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;

/**
 * 하루가 끝나는 시점의 주간 랭킹을 찍어 둔다 (BEA-299).
 *
 * <p>랭킹 상승 미션의 기준값이다. 현재 순위는 언제든 다시 계산할 수 있지만 지나간 순위는 남겨 두지
 * 않으면 사라진다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DailyRankSnapshotService {

    /** 보관 기간. 판정은 어제 것만 보지만, 배치가 하루 걸러 돌아도 쓸 수 있게 여유를 둔다. */
    private static final int RETENTION_DAYS = 14;

    private final DailyRankSnapshotRepository dailyRankSnapshotRepository;
    private final RankingSeasonService rankingSeasonService;
    private final ZoneId businessZoneId;
    private final Clock clock;

    @Transactional
    public SnapshotResult captureToday() {
        Instant now = clock.instant();
        LocalDate snapshotDate = LocalDate.ofInstant(now, businessZoneId);

        // 보관 기간 정리는 시즌과 무관하다. 시즌이 비어 있다고 오래된 행까지 쌓아 둘 이유가 없다.
        int deletedCount = dailyRankSnapshotRepository.deleteOlderThan(snapshotDate.minusDays(RETENTION_DAYS));

        Optional<RankingSeason> season = rankingSeasonService.findActiveWeeklySeason();
        if (season.isEmpty()) {
            // 주간 시즌이 없으면 비교 기준 자체가 없다. 빈 스냅샷을 남기면 다음 날 판정이 어긋난다.
            log.warn("DAILY_RANK_SNAPSHOT_SKIPPED reason=NO_ACTIVE_WEEKLY_SEASON snapshotDate={}", snapshotDate);
            return new SnapshotResult(snapshotDate, null, 0, deletedCount);
        }

        Long seasonId = season.get().getId();
        // 갱신이 아니라 지우고 다시 찍는다. 그 사이 탈퇴했거나 점수가 0이 된 유저의 옛 순위가
        // 남으면 한 날짜 안에 두 세대의 순위가 섞인다.
        dailyRankSnapshotRepository.deleteBySnapshotDate(snapshotDate);
        int capturedCount = dailyRankSnapshotRepository.capture(seasonId, snapshotDate, now);

        return new SnapshotResult(snapshotDate, seasonId, capturedCount, deletedCount);
    }

    public record SnapshotResult(LocalDate snapshotDate, Long seasonId, int capturedCount, int deletedCount) {
    }
}
