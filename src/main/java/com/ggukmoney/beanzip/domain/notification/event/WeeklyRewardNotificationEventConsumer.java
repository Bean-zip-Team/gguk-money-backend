package com.ggukmoney.beanzip.domain.notification.event;

import com.ggukmoney.beanzip.domain.notification.service.NotificationDeliveryService;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class WeeklyRewardNotificationEventConsumer {

    private final NotificationDeliveryService notificationDeliveryService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onWeeklyRewardAvailable(WeeklyRewardAvailableEvent event) {
        notificationDeliveryService.handleWeeklyRewardAvailable(event);
    }
}
