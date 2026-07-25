package com.ggukmoney.beanzip.domain.notification.scheduler;

import com.ggukmoney.beanzip.domain.notification.service.NotificationDeliveryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationScheduler {

    private static final long MORNING_LOCK_KEY = 1_920_830L;
    private static final long EVENING_LOCK_KEY = 1_920_190L;
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final NotificationDeliveryService notificationDeliveryService;
    private final DataSource dataSource;
    private final Clock clock;

    @Scheduled(cron = "${app.smart-message.schedule.morning-cron:0 30 8 * * *}", zone = "${app.smart-message.schedule.zone:Asia/Seoul}")
    public void scheduleMorningNotifications() {
        withAdvisoryLock(MORNING_LOCK_KEY, () -> notificationDeliveryService.sendMorningNotifications(today()));
    }

    @Scheduled(cron = "${app.smart-message.schedule.evening-cron:0 0 19 * * *}", zone = "${app.smart-message.schedule.zone:Asia/Seoul}")
    public void scheduleEveningNotifications() {
        withAdvisoryLock(EVENING_LOCK_KEY, () -> notificationDeliveryService.sendEveningNotifications(today()));
    }

    private LocalDate today() {
        return LocalDate.now(clock.withZone(KST));
    }

    private void withAdvisoryLock(long lockKey, Runnable task) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement acquire = connection.prepareStatement("SELECT pg_try_advisory_lock(?)");
             PreparedStatement release = connection.prepareStatement("SELECT pg_advisory_unlock(?)")) {
            acquire.setLong(1, lockKey);
            try (ResultSet resultSet = acquire.executeQuery()) {
                if (!resultSet.next() || !resultSet.getBoolean(1)) {
                    return;
                }
            }
            try {
                task.run();
            } finally {
                release.setLong(1, lockKey);
                release.execute();
            }
        } catch (Exception exception) {
            log.error("Failed to execute notification schedule. lockKey={}", lockKey, exception);
        }
    }
}
