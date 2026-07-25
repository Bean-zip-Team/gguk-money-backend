package com.ggukmoney.beanzip.domain.notification.event;

import com.ggukmoney.beanzip.domain.notification.service.NotificationDeliveryService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class WeeklyRewardNotificationEventConsumer {

    private final NotificationDeliveryService notificationDeliveryService;

    @EventListener
    public void onWeeklyRewardAvailable(WeeklyRewardAvailableEvent event) {
        notificationDeliveryService.handleWeeklyRewardAvailable(event);
    }
}
