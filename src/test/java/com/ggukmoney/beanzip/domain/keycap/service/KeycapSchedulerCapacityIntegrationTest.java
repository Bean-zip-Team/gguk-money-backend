package com.ggukmoney.beanzip.domain.keycap.service;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import com.ggukmoney.beanzip.domain.keycap.entity.KeycapBoxAccount;
import com.ggukmoney.beanzip.domain.keycap.repository.KeycapBoxAccountRepository;
import com.ggukmoney.beanzip.domain.notification.client.TossSmartMessageClient;
import com.ggukmoney.beanzip.domain.notification.scheduler.NotificationScheduler;
import com.ggukmoney.beanzip.domain.notification.service.NotificationDeliveryService;
import com.ggukmoney.beanzip.domain.notification.service.WeeklyRankingResetNotificationService;
import com.ggukmoney.beanzip.domain.point.entity.PointAccount;
import com.ggukmoney.beanzip.domain.point.repository.PointAccountRepository;
import com.ggukmoney.beanzip.domain.ranking.service.RankingSeasonService;
import com.ggukmoney.beanzip.domain.tap.dto.request.TapBatchSubmitRequest;
import com.ggukmoney.beanzip.domain.tap.entity.UserTapProgress;
import com.ggukmoney.beanzip.domain.tap.entity.UserTapSession;
import com.ggukmoney.beanzip.domain.tap.repository.UserTapProgressRepository;
import com.ggukmoney.beanzip.domain.tap.repository.UserTapSessionRepository;
import com.ggukmoney.beanzip.domain.tap.service.TapBatchService;
import com.ggukmoney.beanzip.domain.user.entity.AppUser;
import com.ggukmoney.beanzip.domain.user.repository.AppUserRepository;
import com.ggukmoney.beanzip.domain.user.service.UserRewardLock;
import com.ggukmoney.beanzip.global.config.*;
import com.ggukmoney.beanzip.global.config.entity.AppConfig;
import com.ggukmoney.beanzip.global.config.repository.AppConfigRepository;
import com.ggukmoney.beanzip.support.FullStackIntegrationTestSupport;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.AopTestUtils;

import javax.sql.DataSource;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

/** Capacity characterization, not an assertion of production throughput or a new pool default. */
class KeycapSchedulerCapacityIntegrationTest extends FullStackIntegrationTestSupport {
    @DynamicPropertySource
    static void capacitySettings(DynamicPropertyRegistry registry) {
        // The same test is run at ten (RED) and twenty (GREEN); CI verifies the deployment candidate.
        registry.add("spring.datasource.hikari.maximum-pool-size",
                () -> System.getenv().getOrDefault("BEA349_TEST_POOL_SIZE", "20"));
        registry.add("spring.datasource.hikari.connection-timeout", () -> "3000");
        registry.add("spring.task.scheduling.pool.size", () -> "5");
        // Invoke real entry points deterministically below instead of depending on the wall-clock minute.
        for (String job : List.of("morning", "evening", "daily-mission", "keycap-box", "weekly-reset")) {
            registry.add("app.smart-message.schedule." + job + "-cron", () -> "-");
        }
    }

    @Autowired AppUserRepository users;
    @Autowired PointAccountRepository accounts;
    @Autowired KeycapBoxAccountRepository wallets;
    @Autowired UserTapProgressRepository progress;
    @Autowired UserTapSessionRepository sessions;
    @Autowired AppConfigRepository configs;
    @Autowired KeycapPassivePolicyConfig passivePolicy;
    @Autowired TapPolicyConfig tapPolicy;
    @Autowired TapBatchService taps;
    @Autowired RankingSeasonService seasons;
    @Autowired NotificationScheduler notifications;
    @Autowired ThreadPoolTaskScheduler taskScheduler;
    @Autowired DataSource dataSource;
    // Only the external transport is replaced. Spies below preserve real database calls and add timing gates.
    @MockitoBean TossSmartMessageClient externalMessages;
    @MockitoSpyBean NotificationDeliveryService deliveries;
    @MockitoSpyBean WeeklyRankingResetNotificationService resetNotifications;
    @MockitoSpyBean UserRewardLock rewardLock;
    @MockitoSpyBean AppConfigBatchLoader loader;

    @BeforeEach
    void prepare() {
        var now = Instant.now();
        configs.save(AppConfig.createFor(KeycapBoxPolicyConfig.KEY_SHARD_ENABLED, "true", now));
        jdbcTemplate.update("delete from app_config where config_key=?", KeycapPassivePolicyConfig.KEY_ENABLED);
        configs.saveAll(KeycapPassivePolicyConfig.DEFAULT_VALUES.entrySet().stream().map(entry ->
                AppConfig.createFor(entry.getKey(), entry.getKey().equals(KeycapPassivePolicyConfig.KEY_ENABLED)
                        ? "true" : entry.getValue(), entry.getKey().equals(KeycapPassivePolicyConfig.KEY_ENABLED)
                        ? Instant.EPOCH : now)).toList());
        configs.save(AppConfig.createFor(TapPolicyConfig.KEY_RATE_LIMIT_ENABLED, "false", now));
        passivePolicy.refresh();
        tapPolicy.refresh();
        seasons.ensureCurrentWeeklySeason(now);
        jdbcTemplate.update("delete from ranking_entry where season_id in (select id from ranking_season where ranking_type='ALL_TIME')");
        jdbcTemplate.update("delete from ranking_season where ranking_type='ALL_TIME'");
    }

    @Test
    void coldSeasonAndTwoRealNotificationJobsCompleteWithoutHiddenErrors() throws Exception {
        runLoad(2);
    }

    @Test
    void coldSeasonAndFiveRetainedAdvisoryJobsCompleteWithoutHiddenErrors() throws Exception {
        runLoad(5);
    }

    private void runLoad(int jobCount) throws Exception {
        var pool = dataSource.unwrap(HikariDataSource.class);
        var usersUnderLoad = new ArrayList<AppUser>();
        for (int i = 0; i < 32; i++) usersUnderLoad.add(createUser());
        var jobsAcquired = new CountDownLatch(jobCount);
        var requestsAcquired = new CountDownLatch(8);
        var finishedLoad = new CountDownLatch(1);
        var firstRequests = new AtomicInteger();
        var completedJobs = new AtomicInteger();
        var economicReads = new LongAdder();
        var passiveReads = new LongAdder();
        var durations = new ConcurrentLinkedQueue<Long>();
        var peakActive = new AtomicInteger();
        var peakWaiting = new AtomicInteger();

        org.mockito.stubbing.Answer<Object> jobGate = invocation -> {
            if (!Thread.currentThread().getName().startsWith("capacity-job-")) return invocation.callRealMethod();
            // AdvisoryLockRunner has already acquired and retained its real PostgreSQL connection.
            jobsAcquired.countDown();
            assertThat(requestsAcquired.await(10, TimeUnit.SECONDS)).as("eight outer tap transactions overlap retained job connections").isTrue();
            Object result = invocation.callRealMethod();
            // Retain the outer advisory connection after real work to model a slow job for the whole load.
            assertThat(finishedLoad.await(90, TimeUnit.SECONDS)).as("load completes before releasing job connections").isTrue();
            completedJobs.incrementAndGet();
            return result;
        };
        doAnswer(jobGate).when(deliveries).sendKeycapBoxOpenAvailableNotifications(any());
        doAnswer(jobGate).when(resetNotifications).enqueueNextPreferencePage();
        if (jobCount == 5) {
            doAnswer(jobGate).when(deliveries).sendMorningNotifications(any());
            doAnswer(jobGate).when(deliveries).sendEveningNotifications(any());
            doAnswer(jobGate).when(deliveries).sendDailyMissionNotifications(any());
        }
        doAnswer(invocation -> {
            Object result = invocation.callRealMethod();
            if (Thread.currentThread().getName().startsWith("capacity-request-") && firstRequests.getAndIncrement() < 8) {
                requestsAcquired.countDown();
                assertThat(requestsAcquired.await(10, TimeUnit.SECONDS)).as("eight tap transactions obtained connections").isTrue();
            }
            return result;
        }).when(AopTestUtils.<UserRewardLock>getUltimateTargetObject(rewardLock)).acquire(any());
        doAnswer(invocation -> {
            if (Thread.currentThread().getName().startsWith("capacity-request-")) {
                String key = invocation.getArgument(1);
                if (KeycapBoxPolicyConfig.KEY_SHARD_ENABLED.equals(key)) economicReads.increment();
                if (KeycapPassivePolicyConfig.KEY_ENABLED.equals(key)) passiveReads.increment();
            }
            return invocation.callRealMethod();
        }).when(loader).loadWithHistory(any(), any(), any());

        var jobs = new ArrayList<Future<?>>();
        var running = new java.util.concurrent.atomic.AtomicBoolean(true);
        try (var errors = new ErrorCapture(); var workers = Executors.newFixedThreadPool(8, task -> new Thread(task, "capacity-request-"));
             var sampler = Executors.newSingleThreadExecutor()) {
            var sampling = sampler.submit(() -> {
                while (running.get()) {
                    peakActive.accumulateAndGet(pool.getHikariPoolMXBean().getActiveConnections(), Math::max);
                    peakWaiting.accumulateAndGet(pool.getHikariPoolMXBean().getThreadsAwaitingConnection(), Math::max);
                    try { Thread.sleep(5); } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); return; }
                }
            });
            List<Runnable> entryPoints = List.of(notifications::scheduleKeycapBoxOpenAvailableNotifications,
                    notifications::scheduleWeeklyRankingResetNotifications, notifications::scheduleMorningNotifications,
                    notifications::scheduleEveningNotifications, notifications::scheduleDailyMissionNotifications);
            try {
                for (Runnable entry : entryPoints.subList(0, jobCount)) jobs.add(taskScheduler.submit(() -> {
                    String original = Thread.currentThread().getName();
                    Thread.currentThread().setName("capacity-job-" + original);
                    try { entry.run(); } finally { Thread.currentThread().setName(original); }
                }));
                assertThat(jobsAcquired.await(10, TimeUnit.SECONDS)).as("all real jobs obtained advisory locks").isTrue();
                var requests = usersUnderLoad.stream().map(user -> workers.submit(() -> {
                    UUID session = UUID.randomUUID();
                    for (long sequence = 1; sequence <= 20; sequence++) {
                        long started = System.nanoTime();
                        var response = taps.submitBatch(user.getId(), new TapBatchSubmitRequest(session, sequence, 1));
                        durations.add(System.nanoTime() - started);
                        assertThat(response.effectiveCount()).isEqualTo(1);
                    }
                    return true;
                })).toList();
                for (var request : requests) assertThat(request.get(90, TimeUnit.SECONDS)).isTrue();
            } finally {
                finishedLoad.countDown();
                running.set(false);
                for (var job : jobs) job.get(15, TimeUnit.SECONDS);
                sampling.get(5, TimeUnit.SECONDS);
            }
            assertThat(completedJobs.get()).isEqualTo(jobCount);
            errors.assertNone();
        } finally {
            running.set(false);
            finishedLoad.countDown();
            // Release timing gates even on the deliberately failing pool-ten reproduction.
            while (requestsAcquired.getCount() > 0) requestsAcquired.countDown();
        }
        assertThat(economicReads.sum()).isEqualTo(640);
        assertThat(passiveReads.sum()).isEqualTo(640);
        for (var user : usersUnderLoad) {
            var totals = progress.findByUserId(user.getId()).orElseThrow();
            assertThat(totals.getCumulativeMissionTapCount()).isEqualTo(20);
            assertThat(totals.getCumulativeRankingTapCount()).isEqualTo(20);
            assertThat(wallets.findByUserId(user.getId()).orElseThrow().getShardBalance()).isEqualTo(101);
            assertThat(accounts.findByUserId(user.getId()).orElseThrow().getBalance()).isEqualTo(1);
            var scores = jdbcTemplate.queryForList("select e.score-coalesce(e.ranking_boost_score,0) from ranking_entry e join ranking_season s on s.id=e.season_id where e.user_id=? and s.status='ACTIVE' and s.ranking_type in ('ALL_TIME','WEEKLY')", Long.class, user.getId());
            assertThat(scores).hasSize(2).allMatch(score -> score == 20L);
            assertThat(jdbcTemplate.queryForObject("select count(*) from keycap_passive_checkpoint where user_id=?", Long.class, user.getId())).isEqualTo(1L);
        }
        var sorted = durations.stream().sorted().toList();
        System.out.printf("SCHEDULER_LOCAL_LOAD users=32 batches=640 workers=8 jobs=%d pool=%d scheduler=%d errors=0 peakActive=%d peakWaiting=%d p50Ms=%.3f p95Ms=%.3f p99Ms=%.3f maxMs=%.3f economicReads=%d passiveReads=%d%n",
                jobCount, pool.getMaximumPoolSize(), taskScheduler.getPoolSize(), peakActive.get(), peakWaiting.get(),
                sorted.get(320)/1e6, sorted.get(608)/1e6, sorted.get(633)/1e6, sorted.get(639)/1e6, economicReads.sum(), passiveReads.sum());
    }

    private AppUser createUser() {
        var now = Instant.now();
        var user = users.save(AppUser.createActive("scheduler-capacity-" + UUID.randomUUID(), null));
        accounts.save(PointAccount.createFor(user));
        var wallet = KeycapBoxAccount.createFor(user, now);
        wallet.addShards(100);
        wallets.save(wallet);
        progress.save(UserTapProgress.createFor(user, 20));
        sessions.save(UserTapSession.createFor(user, now, now.plusSeconds(3600), 10));
        return user;
    }

    private static final class ErrorCapture extends AppenderBase<ILoggingEvent> implements AutoCloseable {
        private final Logger root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        private final Queue<String> errors = new ConcurrentLinkedQueue<>();
        ErrorCapture() { start(); root.addAppender(this); }
        @Override protected void append(ILoggingEvent event) {
            if (event.getLevel().isGreaterOrEqual(Level.ERROR)) errors.add(event.getLoggerName() + ": " + event.getFormattedMessage());
        }
        void assertNone() { assertThat(errors).as("no hidden scheduler, database or ranking errors").isEmpty(); }
        @Override public void close() { root.detachAppender(this); stop(); }
    }
}
