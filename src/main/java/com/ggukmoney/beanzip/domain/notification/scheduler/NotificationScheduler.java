package com.ggukmoney.beanzip.domain.notification.scheduler;

import com.ggukmoney.beanzip.domain.notification.service.NotificationDeliveryService;
import com.ggukmoney.beanzip.global.scheduler.AdvisoryLockRunner;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

@Component
@RequiredArgsConstructor
public class NotificationScheduler {

    private static final long MORNING_LOCK_KEY = 1_920_830L;
    private static final long EVENING_LOCK_KEY = 1_920_190L;
    private static final long KEYCAP_BOX_LOCK_KEY = 1_590_001L;
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final NotificationDeliveryService notificationDeliveryService;
    private final AdvisoryLockRunner advisoryLockRunner;
    private final Clock clock;

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
        return LocalDate.now(clock.withZone(KST));
    }
}
