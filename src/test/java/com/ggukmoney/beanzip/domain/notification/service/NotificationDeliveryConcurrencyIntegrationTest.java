package com.ggukmoney.beanzip.domain.notification.service;

import com.ggukmoney.beanzip.domain.notification.entity.NotificationType;
import com.ggukmoney.beanzip.domain.notification.repository.NotificationDeliveryRepository;
import com.ggukmoney.beanzip.support.FullStackIntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationDeliveryConcurrencyIntegrationTest extends FullStackIntegrationTestSupport {

    @Autowired
    private NotificationDeliveryPersistenceService persistenceService;

    @Autowired
    private NotificationDeliveryRepository deliveryRepository;

    @Test
    void concurrentDedupeRequestsCreateOnlyOneDelivery() throws Exception {
        UUID userId = UUID.randomUUID();
        String dedupeKey = "WEEKLY_REWARD_AVAILABLE:" + userId + ":2026-W30";
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            List<Future<Optional<?>>> futures = List.of(
                    executor.submit(() -> createPendingAfterStart(ready, start, userId, dedupeKey)),
                    executor.submit(() -> createPendingAfterStart(ready, start, userId, dedupeKey))
            );
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            long createdCount = 0;
            for (Future<Optional<?>> future : futures) {
                if (future.get(10, TimeUnit.SECONDS).isPresent()) {
                    createdCount++;
                }
            }
            assertThat(createdCount).isEqualTo(1);
        }

        assertThat(deliveryRepository.findByDedupeKey(dedupeKey)).isPresent();
    }

    private Optional<?> createPendingAfterStart(CountDownLatch ready, CountDownLatch start, UUID userId, String dedupeKey) throws Exception {
        ready.countDown();
        if (!start.await(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException("concurrent test did not start");
        }
        return persistenceService.createPending(userId, NotificationType.WEEKLY_REWARD_AVAILABLE, dedupeKey, "WEEKLY_SET");
    }
}
