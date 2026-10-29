package com.ggukmoney.beanzip.domain.abuse.scheduler;

import com.ggukmoney.beanzip.domain.abuse.client.DiscordWebhookClient;
import com.ggukmoney.beanzip.domain.abuse.service.AbuseDetectionService;
import com.ggukmoney.beanzip.global.scheduler.AdvisoryLockRunner;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * 매시 정각 직후에 직전 1시간의 탭 기록을 보고 어뷰징 의심 유저를 디스코드로 알린다.
 *
 * <p>창은 [직전 정각 - 1시간, 직전 정각) 이라 서로 겹치지 않는다. 한국 시간은 정시 단위 오프셋이라 UTC 로
 * 정시를 잘라도 KST 정시와 같다.
 *
 * <p>ponytail: 서버 재시작으로 실행을 한 번 놓치면 그 한 시간은 다시 보지 않는다. 시범 운영에서는 감수하고,
 * 자동 정지를 붙일 때 마지막 처리 창을 저장해 이어서 보게 한다.
 */
@Slf4j
@Component
public class AbuseDetectionScheduler {

    private static final long ABUSE_DETECTION_LOCK_KEY = 2_990_031L;
    private static final Duration WINDOW = Duration.ofHours(1);

    private final AbuseDetectionService abuseDetectionService;
    private final DiscordWebhookClient discordWebhookClient;
    private final AdvisoryLockRunner advisoryLockRunner;
    private final Clock clock;
    private final boolean enabled;

    public AbuseDetectionScheduler(
            AbuseDetectionService abuseDetectionService,
            DiscordWebhookClient discordWebhookClient,
            AdvisoryLockRunner advisoryLockRunner,
            Clock clock,
            @Value("${app.abuse.detection-enabled:true}") boolean enabled
    ) {
        this.abuseDetectionService = abuseDetectionService;
        this.discordWebhookClient = discordWebhookClient;
        this.advisoryLockRunner = advisoryLockRunner;
        this.clock = clock;
        this.enabled = enabled;
    }

    @Scheduled(
            cron = "${app.abuse.detection-cron:0 5 * * * *}",
            zone = "${app.business-time-zone:Asia/Seoul}"
    )
    public void detectLastHour() {
        if (!enabled) {
            log.info("ABUSE_DETECTION_SKIPPED reason=DISABLED");
            return;
        }

        advisoryLockRunner.runExclusively(ABUSE_DETECTION_LOCK_KEY, () -> {
            Instant windowEnd = clock.instant().truncatedTo(ChronoUnit.HOURS);
            Instant windowStart = windowEnd.minus(WINDOW);
            AbuseDetectionService.Result result = abuseDetectionService.detect(windowStart, windowEnd);
            discordWebhookClient.send(result.alertMessage());
            // 0건이어도 남긴다. 배치가 멈춘 것과 의심 유저가 없던 시간을 구분하기 위해서다.
            log.info("ABUSE_DETECTION_DONE windowStart={} windowEnd={} findings={} suspendCandidates={}",
                    windowStart, windowEnd, result.findings().size(), result.suspendCandidates());
        });
    }
}
