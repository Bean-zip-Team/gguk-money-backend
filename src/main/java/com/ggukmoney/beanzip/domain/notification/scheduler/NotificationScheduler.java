package com.ggukmoney.beanzip.domain.notification.scheduler;

import com.ggukmoney.beanzip.domain.notification.service.FriendNotificationService;
import com.ggukmoney.beanzip.domain.notification.service.NotificationDeliveryService;
import com.ggukmoney.beanzip.domain.notification.service.WeeklyRankingResetNotificationService;
import com.ggukmoney.beanzip.global.scheduler.AdvisoryLockRunner;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

@Slf4j
@Component
public class NotificationScheduler {

    private static final long MORNING_LOCK_KEY = 1_920_830L;
    private static final long EVENING_LOCK_KEY = 1_920_190L;
    private static final long KEYCAP_BOX_LOCK_KEY = 1_590_001L;
    private static final long WEEKLY_RANKING_RESET_LOCK_KEY = 1_580_309L;
    private static final long DAILY_MISSION_LOCK_KEY = 2_990_021L;
    private static final long FRIEND_LOCK_KEY = 3_900_390L;

    private final NotificationDeliveryService notificationDeliveryService;
    private final WeeklyRankingResetNotificationService weeklyRankingResetNotificationService;
    private final FriendNotificationService friendNotificationService;
    private final AdvisoryLockRunner advisoryLockRunner;
    private final Clock clock;
    private final ZoneId scheduleZoneId;
    private final boolean dailyMissionEnabled;

    public NotificationScheduler(
            NotificationDeliveryService notificationDeliveryService,
            WeeklyRankingResetNotificationService weeklyRankingResetNotificationService,
            FriendNotificationService friendNotificationService,
            AdvisoryLockRunner advisoryLockRunner,
            Clock clock,
            // 크론이 도는 시간대와 같은 값이어야 한다. 둘이 갈리면 밤 9시에 깨어나서 어제 날짜로 보낸다.
            @Value("${app.smart-message.schedule.zone:Asia/Seoul}") String scheduleZone,
            // 문구가 잘못 나갔거나 대상이 과하게 잡힐 때 끄는 스위치. 발송은 되돌릴 수 없어 멈출 수단이
            // 필요하다. 캠페인 코드를 지워도 멈추지만, 그러면 알림 설정 토글과 동의 미션까지 함께 사라진다.
            @Value("${app.smart-message.schedule.daily-mission-enabled:true}") boolean dailyMissionEnabled
    ) {
        this.notificationDeliveryService = notificationDeliveryService;
        this.weeklyRankingResetNotificationService = weeklyRankingResetNotificationService;
        this.friendNotificationService = friendNotificationService;
        this.advisoryLockRunner = advisoryLockRunner;
        this.clock = clock;
        this.scheduleZoneId = ZoneId.of(scheduleZone);
        this.dailyMissionEnabled = dailyMissionEnabled;
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
        if (!dailyMissionEnabled) {
            log.info("DAILY_MISSION_NOTIFICATION_SKIPPED reason=DISABLED");
            return;
        }

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

    @Scheduled(
            cron = "${app.smart-message.schedule.weekly-reset-cron:0 * * * * *}",
            zone = "${app.smart-message.schedule.zone:Asia/Seoul}"
    )
    public void scheduleWeeklyRankingResetNotifications() {
        advisoryLockRunner.runExclusively(WEEKLY_RANKING_RESET_LOCK_KEY, () -> {
            weeklyRankingResetNotificationService.enqueueNextPreferencePage();
            notificationDeliveryService.dispatchWeeklyResetDue(100);
            weeklyRankingResetNotificationService.completeOneDrainedBatch();
        });
    }

    /**
     * 보낼 시각이 된 친구 푸시를 보낸다 (BEA-390).
     *
     * <p>낮에 생긴 푸시는 다음 실행 때 나가고, 밤 9시~아침 8시에 생긴 푸시는 아침 8시부터 나간다.
     */
    @Scheduled(
            cron = "${app.smart-message.schedule.friend-cron:0 * * * * *}",
            zone = "${app.smart-message.schedule.zone:Asia/Seoul}"
    )
    public void scheduleFriendNotifications() {
        advisoryLockRunner.runExclusively(FRIEND_LOCK_KEY, friendNotificationService::dispatchDue);
    }

    private LocalDate today() {
        return LocalDate.now(clock.withZone(scheduleZoneId));
    }
}
