package com.ggukmoney.beanzip.domain.mission.scheduler;

import com.ggukmoney.beanzip.domain.mission.service.MissionRewardService;
import com.ggukmoney.beanzip.global.scheduler.AdvisoryLockRunner;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;

/**
 * 자정에 미수령 보상을 마감한다 (BEA-299).
 *
 * <p>받지 않은 보상은 자정에 소멸한다는 것이 제품 결정이다. 다만 행은 남긴다 — 얼마를 놓쳤는지
 * 보여줄 수 있어야 하고, 소멸 규모를 재야 정책이 과한지 판단할 수 있다.
 *
 * <p>자정 정각이 아니라 조금 뒤에 돌린다. 리셋 순간에 들어온 수령 요청과 겹쳐 "방금 눌렀는데
 * 소멸됐다"는 상황을 만들지 않기 위해서다. 수령 경로도 만료 시각을 직접 보므로, 이 배치가 늦게
 * 돌아도 소멸한 보상이 지급되지는 않는다.
 */
@Slf4j
@Component
public class MissionRewardExpiryScheduler {

    private static final long MISSION_EXPIRY_LOCK_KEY = 2_990_005L;

    private final MissionRewardService missionRewardService;
    private final AdvisoryLockRunner advisoryLockRunner;
    private final Clock clock;
    private final boolean enabled;

    public MissionRewardExpiryScheduler(
            MissionRewardService missionRewardService,
            AdvisoryLockRunner advisoryLockRunner,
            Clock clock,
            // 잘못 돌고 있는 것을 발견했을 때 끄는 스위치. 소멸은 되돌릴 수 없어 멈출 수단이 필요하다.
            @Value("${app.mission.reward-expiry-enabled:true}") boolean enabled
    ) {
        this.missionRewardService = missionRewardService;
        this.advisoryLockRunner = advisoryLockRunner;
        this.clock = clock;
        this.enabled = enabled;
    }

    @Scheduled(
            cron = "${app.mission.reward-expiry-cron:0 5 0 * * *}",
            zone = "${app.business-time-zone:Asia/Seoul}"
    )
    public void expireUnclaimedRewards() {
        if (!enabled) {
            log.info("MISSION_REWARD_EXPIRY_SKIPPED reason=DISABLED");
            return;
        }

        advisoryLockRunner.runExclusively(MISSION_EXPIRY_LOCK_KEY, () -> {
            MissionRewardService.ExpiryResult result = missionRewardService.expireDueRewards(clock.instant());
            // 0건이어도 남긴다. 정상 경로에서만 찍으면 배치가 멈춘 상태와 소멸이 없던 날이
            // 똑같이 "로그 없음"으로 보인다.
            log.info("MISSION_REWARD_EXPIRY_DONE expiredCount={} expiredPointAmount={}",
                    result.expiredCount(), result.expiredPointAmount());
        });
    }
}
