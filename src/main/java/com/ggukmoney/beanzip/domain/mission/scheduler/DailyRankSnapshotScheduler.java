package com.ggukmoney.beanzip.domain.mission.scheduler;

import com.ggukmoney.beanzip.domain.mission.service.DailyRankSnapshotService;
import com.ggukmoney.beanzip.global.scheduler.AdvisoryLockRunner;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 하루가 끝나기 직전에 주간 랭킹을 찍는다 (BEA-299).
 *
 * <p>자정 직후가 아니라 <b>자정 직전</b>에 찍는다. 주간 시즌이 초기화되는 월요일 00시에 찍으면
 * 전원이 0점인 무의미한 순서가 기준이 되고, 그러면 월요일뿐 아니라 화요일 판정까지 공짜가 된다.
 */
@Slf4j
@Component
public class DailyRankSnapshotScheduler {

    private static final long DAILY_RANK_SNAPSHOT_LOCK_KEY = 2_992_355L;

    private final DailyRankSnapshotService dailyRankSnapshotService;
    private final AdvisoryLockRunner advisoryLockRunner;
    private final boolean enabled;

    public DailyRankSnapshotScheduler(
            DailyRankSnapshotService dailyRankSnapshotService,
            AdvisoryLockRunner advisoryLockRunner,
            @Value("${app.mission.rank-snapshot-enabled:true}") boolean enabled
    ) {
        this.dailyRankSnapshotService = dailyRankSnapshotService;
        this.advisoryLockRunner = advisoryLockRunner;
        this.enabled = enabled;
    }

    @Scheduled(
            cron = "${app.mission.rank-snapshot-cron:0 55 23 * * *}",
            zone = "${app.business-time-zone:Asia/Seoul}"
    )
    public void captureDailyRankSnapshot() {
        if (!enabled) {
            log.info("DAILY_RANK_SNAPSHOT_SKIPPED reason=DISABLED");
            return;
        }

        advisoryLockRunner.runExclusively(DAILY_RANK_SNAPSHOT_LOCK_KEY, () -> {
            DailyRankSnapshotService.SnapshotResult result = dailyRankSnapshotService.captureToday();
            // 0건이어도 남긴다. 찍히지 않은 날은 다음 날 랭킹 미션이 통째로 사라지므로 추적이 필요하다.
            log.info("DAILY_RANK_SNAPSHOT_DONE snapshotDate={} seasonId={} capturedCount={} deletedCount={}",
                    result.snapshotDate(), result.seasonId(), result.capturedCount(), result.deletedCount());
        });
    }
}
