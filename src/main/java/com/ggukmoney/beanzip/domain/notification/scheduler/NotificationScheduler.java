package com.ggukmoney.beanzip.domain.notification.scheduler;

import com.ggukmoney.beanzip.domain.notification.service.NotificationDeliveryService;
import com.ggukmoney.beanzip.global.scheduler.AdvisoryLockRunner;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

@Component
public class NotificationScheduler {

    private static final long MORNING_LOCK_KEY = 1_920_830L;
    private static final long EVENING_LOCK_KEY = 1_920_190L;
    private static final long KEYCAP_BOX_LOCK_KEY = 1_590_001L;
    private static final long DAILY_MISSION_LOCK_KEY = 2_990_021L;

    private final NotificationDeliveryService notificationDeliveryService;
    private final AdvisoryLockRunner advisoryLockRunner;
    private final Clock clock;
    private final ZoneId scheduleZoneId;

    public NotificationScheduler(
            NotificationDeliveryService notificationDeliveryService,
            AdvisoryLockRunner advisoryLockRunner,
            Clock clock,
            // 크론이 도는 시간대와 같은 값이어야 한다. 둘이 갈리면 밤 9시에 깨어나서 어제 날짜로 보낸다.
            @Value("${app.smart-message.schedule.zone:Asia/Seoul}") String scheduleZone
    ) {
        this.notificationDeliveryService = notificationDeliveryService;
        this.advisoryLockRunner = advisoryLockRunner;
        this.clock = clock;
        this.scheduleZoneId = ZoneId.of(scheduleZone);
    }

    @Scheduled(cron = "${app.smart-message.schedule.morning-cron:0 30 8 * * *}", zone = "${app.smart-message.schedule.zone:Asia/Seoul}")
    public void scheduleMorningNotifications() {
        advisoryLockRunner.runExclusively(
                MORNING_LOCK_KEY, () -> notificationDeliveryService.sendMorningNotifications(today()));
    }

    @Scheduled(cron = "${app.smart-message.schedule.evening-cron:0 0 19 * * *}", zone = "${app.smart-message.schedule.zone:Asia/Seoul}")
    public void scheduleEveningNotifications() {
        advisoryLockRunner.runExclusively(
                EVENING_LOCK_KEY, () -> notificationDeliveryService.sendEveningNotifications(today()));
    }

    /**
     * 밤 9시에 데일리 미션을 상기시킨다 (BEA-299).
     *
     * <p>자정에 미수령 보상이 소멸하므로, 아직 시간이 남아 있을 때 알린다.
     */
    @Scheduled(
            cron = "${app.smart-message.schedule.daily-mission-cron:0 0 21 * * *}",
            zone = "${app.smart-message.schedule.zone:Asia/Seoul}"
    )
    public void scheduleDailyMissionNotifications() {
        advisoryLockRunner.runExclusively(
                DAILY_MISSION_LOCK_KEY, () -> notificationDeliveryService.sendDailyMissionNotifications(today()));
    }

    @Scheduled(
            cron = "${app.smart-message.schedule.keycap-box-cron:0 * * * * *}",
            zone = "${app.smart-message.schedule.zone:Asia/Seoul}"
    )
    public void scheduleKeycapBoxOpenAvailableNotifications() {
        advisoryLockRunner.runExclusively(
                KEYCAP_BOX_LOCK_KEY,
                () -> notificationDeliveryService.sendKeycapBoxOpenAvailableNotifications(clock.instant())
        );
    }

    private LocalDate today() {
        return LocalDate.now(clock.withZone(scheduleZoneId));
    }
}
