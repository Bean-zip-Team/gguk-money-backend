package com.ggukmoney.beanzip.domain.notification.repository;

import com.ggukmoney.beanzip.domain.notification.entity.NotificationDelivery;
import com.ggukmoney.beanzip.domain.notification.entity.NotificationDeliveryStatus;
import com.ggukmoney.beanzip.domain.notification.entity.NotificationType;
import com.ggukmoney.beanzip.support.FullStackIntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Transactional
class NotificationDeliveryRepositoryIntegrationTest extends FullStackIntegrationTestSupport {

    @Autowired
    private NotificationDeliveryRepository deliveryRepository;

    @Test
    void recentSentBatchFiltersUserTypeStatusAndExclusiveCutoff() {
        UUID afterUser = UUID.randomUUID();
        UUID equalUser = UUID.randomUUID();
        UUID failedUser = UUID.randomUUID();
        UUID retryWaitingUser = UUID.randomUUID();
        UUID otherTypeUser = UUID.randomUUID();
        Instant cutoff = Instant.parse("2026-07-25T04:00:00Z");
        deliveryRepository.save(sent(afterUser, NotificationType.RANK_CHANGE, cutoff.plusSeconds(1), "after"));
        deliveryRepository.save(sent(equalUser, NotificationType.RANK_CHANGE, cutoff, "equal"));
        deliveryRepository.save(failed(failedUser, cutoff.plusSeconds(1), "failed"));
        deliveryRepository.save(retryWaiting(retryWaitingUser, cutoff.plusSeconds(1), "retry-waiting"));
        deliveryRepository.save(sent(otherTypeUser, NotificationType.DAILY_REMINDER, cutoff.plusSeconds(1), "other"));
        deliveryRepository.flush();

        assertThat(deliveryRepository.findUserIdsWithRecentDelivery(
                List.of(afterUser, equalUser, failedUser, retryWaitingUser, otherTypeUser),
                NotificationType.RANK_CHANGE,
                NotificationDeliveryStatus.SENT,
                cutoff
        )).containsExactly(afterUser);
    }

    private NotificationDelivery sent(UUID userId, NotificationType type, Instant requestedAt, String suffix) {
        NotificationDelivery delivery = NotificationDelivery.pending(
                userId, type, "sent-" + suffix + "-" + userId, "template", requestedAt);
        delivery.markSent("content-" + suffix, "{}");
        return delivery;
    }

    private NotificationDelivery failed(UUID userId, Instant requestedAt, String suffix) {
        NotificationDelivery delivery = NotificationDelivery.pending(
                userId, NotificationType.RANK_CHANGE, "failed-" + suffix + "-" + userId, "template", requestedAt);
        delivery.markFailed("FAILED", "failed", "{}");
        return delivery;
    }

    private NotificationDelivery retryWaiting(UUID userId, Instant requestedAt, String suffix) {
        NotificationDelivery delivery = NotificationDelivery.pending(
                userId, NotificationType.RANK_CHANGE, "retry-" + suffix + "-" + userId, "template", requestedAt);
        delivery.markRetryWaiting("RETRY_WAITING", "retry waiting", "{}");
        return delivery;
    }
}
