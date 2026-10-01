package com.ggukmoney.beanzip.domain.notification.service;

import com.ggukmoney.beanzip.domain.keycap.entity.KeycapBoxAccount;
import com.ggukmoney.beanzip.domain.keycap.repository.KeycapBoxAccountRepository;
import com.ggukmoney.beanzip.domain.notification.entity.NotificationPreference;
import com.ggukmoney.beanzip.domain.notification.entity.NotificationType;
import com.ggukmoney.beanzip.domain.notification.repository.NotificationDeliveryRepository;
import com.ggukmoney.beanzip.domain.notification.repository.NotificationPreferenceRepository;
import com.ggukmoney.beanzip.domain.notification.repository.WeeklyRankingResetNotificationBatchRepository;
import com.ggukmoney.beanzip.domain.notification.event.WeeklyRankingSeasonClosedNotificationListener;
import com.ggukmoney.beanzip.domain.ranking.entity.RankingEntry;
import com.ggukmoney.beanzip.domain.ranking.entity.RankingSeason;
import com.ggukmoney.beanzip.domain.ranking.event.WeeklyRankingSeasonClosedEvent;
import com.ggukmoney.beanzip.domain.ranking.repository.RankingEntryRepository;
import com.ggukmoney.beanzip.domain.ranking.repository.RankingSeasonRepository;
import com.ggukmoney.beanzip.domain.user.entity.AppUser;
import com.ggukmoney.beanzip.domain.user.repository.AppUserRepository;
import com.ggukmoney.beanzip.support.FullStackIntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.LocalDate;
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

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private KeycapBoxAccountRepository keycapBoxAccountRepository;

    @Autowired
    private NotificationPreferenceRepository preferenceRepository;

    @Autowired
    private WeeklyRankingResetNotificationBatchRepository resetBatchRepository;

    @Autowired
    private RankingSeasonRepository rankingSeasonRepository;

    @Autowired
    private RankingEntryRepository rankingEntryRepository;

    @Autowired
    private WeeklyRankingSeasonClosedNotificationListener resetNotificationListener;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @DynamicPropertySource
    static void registerKeycapCampaign(DynamicPropertyRegistry registry) {
        registry.add(
                "app.smart-message.templates.keycap-box-open-available.campaign-code",
                () -> "clickmoney-box"
        );
    }

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

    @Test
    void seasonClosedEventCreatesBatchAndTheInsertRollsBackWithItsTransaction() {
        RankingSeason committed = rankingSeasonRepository.saveAndFlush(closedWeekly("2031-01-06"));

        new TransactionTemplate(transactionManager).executeWithoutResult(
                status -> eventPublisher.publishEvent(new WeeklyRankingSeasonClosedEvent(committed.getId())));

        assertThat(resetBatchRepository.findBySeasonId(committed.getId())).isPresent();

        RankingSeason rolledBack = rankingSeasonRepository.saveAndFlush(closedWeekly("2031-01-13"));
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            resetNotificationListener.onSeasonClosed(new WeeklyRankingSeasonClosedEvent(rolledBack.getId()));
            status.setRollbackOnly();
        });

        assertThat(resetBatchRepository.findBySeasonId(rolledBack.getId())).isEmpty();
    }

    @Test
    void concurrentSeasonClosedBatchCreationCreatesOnlyOneBatch() throws Exception {
        RankingSeason season = rankingSeasonRepository.saveAndFlush(closedWeekly("2031-01-20"));
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            List<Future<Integer>> futures = List.of(
                    executor.submit(() -> insertBatchAfterStart(ready, start, season.getId())),
                    executor.submit(() -> insertBatchAfterStart(ready, start, season.getId()))
            );
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            assertThat(futures).extracting(future -> future.get(10, TimeUnit.SECONDS))
                    .containsExactlyInAnyOrder(0, 1);
        }

        assertThat(resetBatchRepository.findBySeasonId(season.getId())).isPresent();
    }

    @Test
    void concurrentWeeklyResetEnqueueCreatesOneDeliveryPerUserAndSeason() throws Exception {
        RankingSeason season = rankingSeasonRepository.saveAndFlush(closedWeekly("2031-01-27"));
        AppUser user = appUserRepository.saveAndFlush(AppUser.createActive("weekly-reset-concurrent", null));
        RankingEntry entry = RankingEntry.createFor(season, user, 100L, null, season.getEndsAt());
        entry.finalizeRank(1L, season.getEndsAt().plusSeconds(600));
        rankingEntryRepository.saveAndFlush(entry);
        NotificationPreference preference = NotificationPreference.defaultOf(user.getId(), NotificationType.RANK_CHANGE);
        preference.applyAgreement("newAgreement");
        preferenceRepository.saveAndFlush(preference);
        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                resetBatchRepository.insertIfAbsent(season.getId(), season.getEndsAt().plusSeconds(600)));
        Long batchId = resetBatchRepository.findBySeasonId(season.getId()).orElseThrow().getId();
        String dedupeKey = "RANK_CHANGE:WEEKLY_RESET:%d:%s".formatted(season.getId(), user.getId());
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            List<Future<Integer>> futures = List.of(
                    executor.submit(() -> insertWeeklyResetAfterStart(
                            ready, start, user.getId(), batchId, season.getId(), dedupeKey)),
                    executor.submit(() -> insertWeeklyResetAfterStart(
                            ready, start, user.getId(), batchId, season.getId(), dedupeKey))
            );
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            assertThat(futures).extracting(future -> future.get(10, TimeUnit.SECONDS))
                    .containsExactlyInAnyOrder(0, 1);
        }

        assertThat(deliveryRepository.findByDedupeKey(dedupeKey)).isPresent();
    }

    @Test
    void concurrentKeycapPreparationCreatesOneDeliveryAndRefreshesOneCycle() throws Exception {
        AppUser user = appUserRepository.save(AppUser.createActive("keycap-concurrent", null));
        Instant cycleStartedAt = Instant.parse("2026-08-03T00:00:00Z");
        Instant now = Instant.parse("2026-08-03T01:00:01Z");
        KeycapBoxAccount account = KeycapBoxAccount.createFor(user, cycleStartedAt);
        ReflectionTestUtils.setField(account, "boxBalance", 1);
        ReflectionTestUtils.setField(account, "freeOpenUsedCount", 2);
        ReflectionTestUtils.setField(account, "adOpenUsedCount", 6);
        keycapBoxAccountRepository.saveAndFlush(account);
        NotificationPreference preference = NotificationPreference.defaultOf(
                user.getId(), NotificationType.KEYCAP_BOX_OPEN_AVAILABLE
        );
        preference.applyAgreement("newAgreement");
        preferenceRepository.saveAndFlush(preference);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            List<Future<Optional<?>>> futures = List.of(
                    executor.submit(() -> prepareKeycapAfterStart(ready, start, user.getId(), now)),
                    executor.submit(() -> prepareKeycapAfterStart(ready, start, user.getId(), now))
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

        KeycapBoxAccount refreshed = keycapBoxAccountRepository.findByUserId(user.getId()).orElseThrow();
        assertThat(refreshed.getOpenCycleStartedAt()).isEqualTo(Instant.parse("2026-08-03T01:00:00Z"));
        assertThat(refreshed.getFreeOpenUsedCount()).isZero();
        assertThat(refreshed.getAdOpenUsedCount()).isZero();
        assertThat(deliveryRepository.findByDedupeKey(
                "KEYCAP_BOX_OPEN_AVAILABLE:" + user.getId() + ":2026-08-03T01:00:00Z"
        )).isPresent();
    }

    private Optional<?> createPendingAfterStart(CountDownLatch ready, CountDownLatch start, UUID userId, String dedupeKey) throws Exception {
        ready.countDown();
        if (!start.await(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException("concurrent test did not start");
        }
        return persistenceService.createPending(userId, NotificationType.WEEKLY_REWARD_AVAILABLE, dedupeKey, "WEEKLY_SET");
    }

    private Optional<?> prepareKeycapAfterStart(
            CountDownLatch ready,
            CountDownLatch start,
            UUID userId,
            Instant now
    ) throws Exception {
        ready.countDown();
        if (!start.await(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException("concurrent test did not start");
        }
        return persistenceService.prepareKeycapBoxOpenAvailable(userId, now);
    }

    private int insertBatchAfterStart(
            CountDownLatch ready, CountDownLatch start, Long seasonId
    ) throws Exception {
        ready.countDown();
        if (!start.await(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException("concurrent test did not start");
        }
        return new TransactionTemplate(transactionManager).execute(status ->
                resetBatchRepository.insertIfAbsent(seasonId, Instant.now()));
    }

    private int insertWeeklyResetAfterStart(
            CountDownLatch ready,
            CountDownLatch start,
            UUID userId,
            Long batchId,
            Long seasonId,
            String dedupeKey
    ) throws Exception {
        ready.countDown();
        if (!start.await(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException("concurrent test did not start");
        }
        return new TransactionTemplate(transactionManager).execute(status ->
                deliveryRepository.insertWeeklyResetPendingIfAbsent(
                        UUID.randomUUID(), userId, batchId, seasonId, dedupeKey, "clickmoney-asfasf", Instant.now()));
    }

    private RankingSeason closedWeekly(String weekStart) {
        LocalDate startDate = LocalDate.parse(weekStart);
        Instant startsAt = startDate.atStartOfDay(java.time.ZoneId.of("Asia/Seoul")).toInstant();
        RankingSeason season = RankingSeason.activeWeekly(startDate, startsAt, startsAt.plusSeconds(604800));
        season.startFinalizing();
        season.close(season.getEndsAt().plusSeconds(600));
        return season;
    }
}
