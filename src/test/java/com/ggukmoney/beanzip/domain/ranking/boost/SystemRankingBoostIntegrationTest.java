package com.ggukmoney.beanzip.domain.ranking.boost;

import com.ggukmoney.beanzip.domain.ranking.entity.*;
import com.ggukmoney.beanzip.domain.ranking.repository.*;
import com.ggukmoney.beanzip.domain.ranking.reward.WeeklyRankingRewardPolicy;
import com.ggukmoney.beanzip.domain.ranking.service.*;
import com.ggukmoney.beanzip.domain.ranking.redis.RankingRedisRepository;
import com.ggukmoney.beanzip.domain.tap.entity.UserTapDaily;
import com.ggukmoney.beanzip.domain.tap.repository.UserTapDailyRepository;
import com.ggukmoney.beanzip.domain.user.entity.AppUser;
import com.ggukmoney.beanzip.domain.user.repository.AppUserRepository;
import com.ggukmoney.beanzip.domain.notification.entity.*;
import com.ggukmoney.beanzip.domain.notification.repository.*;
import com.ggukmoney.beanzip.global.config.entity.AppConfig;
import com.ggukmoney.beanzip.global.config.repository.AppConfigRepository;
import com.ggukmoney.beanzip.support.FullStackIntegrationTestSupport;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.test.context.TestPropertySource;
import com.ggukmoney.beanzip.domain.auth.entity.AuthIdentity;
import com.ggukmoney.beanzip.domain.auth.repository.AuthIdentityRepository;
import com.ggukmoney.beanzip.domain.notification.client.TossSmartMessageClient;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@TestPropertySource(properties = "app.smart-message.templates.rank-change.campaign-code=RANK_SET")
class SystemRankingBoostIntegrationTest extends FullStackIntegrationTestSupport {
    private static final Instant NOW = Instant.parse("2026-09-18T10:00:00Z");
    private static final LocalDate DATE = LocalDate.of(2026, 9, 18);
    @Autowired SystemRankingBoostTransactionService service;
    @Autowired RankingBoostRunRepository runs;
    @Autowired RankingBoostRewardExclusions exclusions;
    @MockitoSpyBean RankingEntryRepository entries;
    @Autowired RankingSeasonService seasonService;
    @Autowired RankingSeasonRepository seasons;
    @Autowired AppUserRepository users;
    @Autowired UserTapDailyRepository taps;
    @Autowired AppConfigRepository configs;
    @Autowired NotificationPreferenceRepository preferences;
    @Autowired NotificationDeliveryRepository deliveries;
    @Autowired RankingProjectionService projection;
    @Autowired RankingBackfillService backfill;
    @MockitoSpyBean RankingRedisRepository rankingRedis;
    @Autowired RankingReconciliationService reconciliation;
    @Autowired PlatformTransactionManager tm;
    @Autowired SystemRankingBoostScheduler scheduler;
    @Autowired AuthIdentityRepository identities;
    @MockitoBean TossSmartMessageClient smartMessages;
    @MockitoBean SystemRankingBoostRolloutGate gate;
    @MockitoBean RankingBoostRandom random;
    @MockitoBean Clock clock;
    RankingSeason season;
    AppUser leader;
    AppUser staff;
    Instant configAt;

    @BeforeEach
    void fixture() {
        when(clock.instant()).thenReturn(NOW);
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);
        when(clock.withZone(any())).thenAnswer(inv -> Clock.fixed(NOW, inv.getArgument(0)));
        jdbcTemplate.execute("TRUNCATE ranking_boost_run, ranking_entry, ranking_season, notification_rank_state, notification_delivery, notification_preference, user_tap_daily CASCADE");
        jdbcTemplate.update(
                "DELETE FROM app_config WHERE config_key IN (?, ?)",
                SystemRankingBoostPolicy.KEY,
                WeeklyRankingRewardPolicy.KEY
        );
        season = seasons.saveAndFlush(RankingSeason.activeWeekly(LocalDate.of(2026, 9, 14),
                Instant.parse("2026-09-13T15:00:00Z"), Instant.parse("2026-09-20T15:00:00Z")));
        leader = users.saveAndFlush(AppUser.createActive("real", null));
        staff = users.saveAndFlush(AppUser.createActive("staff", null));
        entries.saveAndFlush(RankingEntry.createFor(season, leader, 1500L, null, NOW));
        UserTapDaily daily = UserTapDaily.createFor(leader, DATE);
        daily.addValidTaps(1500); daily.addTotalValidTaps(1500);
        taps.saveAndFlush(daily);
        configAt = NOW.minusSeconds(60);
        configs.saveAndFlush(AppConfig.createFor(
                WeeklyRankingRewardPolicy.KEY,
                "{\"enabled\":false,\"rewards\":{\"1\":10000,\"2\":5000,\"3\":2500}}",
                configAt
        ));
        policy(true, List.of(staff.getId()), 1000);
        when(gate.permits(any(), any())).thenReturn(true);
        when(random.scheduledAt(any())).thenReturn(Instant.parse("2026-09-18T09:00:00Z"));
        when(random.increment(200, 500)).thenReturn(300);
        service.plan(NOW);
    }

    @Test
    void appliesOnceWithoutRealTapsAndHistoricalRewardExclusionSurvivesListRemoval() {
        long realProgressBefore = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM user_tap_progress WHERE user_id = ?", Long.class, staff.getId());
        long tapEventsBefore = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM tap_batch WHERE user_id = ?", Long.class, staff.getId());
        RankingBoostRun run = service.advance(NOW).orElseThrow();
        assertThat(run.getStatus()).isEqualTo(RankingBoostRun.Status.APPLIED);
        assertThat(run.getEventType()).isEqualTo("SYSTEM_RANKING_BOOST");
        assertThat(run.getPreviousScore()).isZero();
        assertThat(run.getTargetScore()).isEqualTo(1800L);
        service.advance(NOW.plusSeconds(300));
        assertThat(runs.count()).isEqualTo(1);
        assertThat(rankingRedis.findScore(season.getId(), staff.getId())).isEqualTo(1800L);
        assertThat(taps.findByUserIdAndTapDate(staff.getId(), DATE)).isEmpty();
        assertThat(taps.findByUserIdAndTapDate(leader.getId(), DATE).orElseThrow().getTotalValidTapCount()).isEqualTo(1500);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM user_tap_progress WHERE user_id = ?", Long.class, staff.getId())).isEqualTo(realProgressBefore);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM tap_batch WHERE user_id = ?", Long.class, staff.getId())).isEqualTo(tapEventsBefore);
        configAt = NOW.minusSeconds(30); policy(false, List.of(), 1000);
        assertThat(exclusions.excludedUserIds(season.getId(), NOW).orElseThrow()).contains(staff.getId());
        assertThat(exclusions.excludedUserIds(season.getId() + 100, NOW).orElseThrow()).doesNotContain(staff.getId());
    }

    @Test
    void boostSurvivesProjectionAndFinalizingRecountIncludingNoRealTapEntry() {
        service.advance(NOW);
        projection.syncLatestWeeklyScore(staff.getId(), NOW);
        assertThat(entries.findBySeasonAndUserId(season, staff.getId()).orElseThrow().getScore()).isEqualTo(1800L);
        new TransactionTemplate(tm).executeWithoutResult(tx -> {
            RankingSeason loaded = seasons.findById(season.getId()).orElseThrow();
            loaded.startFinalizing();
        });
        backfill.backfillFinalizingWeeklySeason(season, LocalDate.of(2026, 9, 14), LocalDate.of(2026, 9, 21));
        assertThat(entries.findBySeasonAndUserId(season, staff.getId()).orElseThrow().getScore()).isEqualTo(1800L);
        assertThat(entries.findBySeasonAndUserId(season, staff.getId()).orElseThrow().getRankingBoostScore()).isEqualTo(1800L);
    }

    @Test
    void concurrentTicksCommitExactlyOneBoost() throws Exception {
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            CountDownLatch start = new CountDownLatch(1);
            Callable<Void> call = () -> { start.await(); service.advance(NOW); return null; };
            Future<Void> one = executor.submit(call); Future<Void> two = executor.submit(call);
            start.countDown(); one.get(20, TimeUnit.SECONDS); two.get(20, TimeUnit.SECONDS);
        }
        assertThat(runs.count()).isEqualTo(1);
        assertThat(entries.findBySeasonAndUserId(season, staff.getId()).orElseThrow().getRankingBoostScore()).isEqualTo(1800L);
    }

    @Test
    void dynamicThresholdSkipsAndKillSwitchOrClosedGateDoesNotApply() {
        configAt = NOW.minusSeconds(30); policy(true, List.of(staff.getId()), 1600);
        assertThat(service.advance(NOW).orElseThrow().getSkipReason()).isEqualTo("LEADER_BELOW_MINIMUM");
        assertThat(entries.findBySeasonAndUserId(season, staff.getId())).isEmpty();
        runs.deleteAll(); configAt = NOW.minusSeconds(20); policy(false, List.of(staff.getId()), 1);
        assertThat(service.advance(NOW)).isEmpty(); assertThat(runs.count()).isZero();
        configAt = NOW.minusSeconds(10); policy(true, List.of(staff.getId()), 1);
        when(gate.permits(any(), any())).thenReturn(false);
        assertThat(service.advance(NOW)).isEmpty(); assertThat(runs.count()).isZero();
    }

    @Test
    void fixedScheduleSurvivesTicksAndAfterWindowExpiresWithoutBoost() {
        Instant target = Instant.parse("2026-09-18T12:00:00Z");
        runs.deleteAll();
        when(random.scheduledAt(DATE)).thenReturn(target);
        service.plan(NOW);
        assertThat(service.advance(NOW).orElseThrow().getScheduledAt()).isEqualTo(target);
        when(random.scheduledAt(DATE)).thenReturn(NOW);
        assertThat(service.advance(NOW.plusSeconds(300)).orElseThrow().getScheduledAt()).isEqualTo(target);
        assertThat(service.advance(Instant.parse("2026-09-18T13:00:00Z")).orElseThrow().getSkipReason()).isEqualTo("WINDOW_EXPIRED");
        assertThat(entries.findBySeasonAndUserId(season, staff.getId())).isEmpty();
    }

    @Test
    void plannedScheduleIsDurableWhenApplyTransactionRollsBack() {
        service.plan(NOW);
        when(random.increment(200, 500)).thenThrow(new IllegalStateException("random unavailable"));
        assertThatThrownBy(() -> service.advance(NOW)).isInstanceOf(IllegalStateException.class);
        assertThat(runs.findByRunDate(DATE).orElseThrow().getStatus()).isEqualTo(RankingBoostRun.Status.PLANNED);
        assertThat(runs.findByRunDate(DATE).orElseThrow().getScheduledAt()).isEqualTo(Instant.parse("2026-09-18T09:00:00Z"));
        assertThat(entries.findBySeasonAndUserId(season, staff.getId())).isEmpty();
        doReturn(300).when(random).increment(200, 500);
        when(random.scheduledAt(DATE)).thenReturn(NOW.plusSeconds(3600));
        service.plan(NOW);
        assertThat(service.advance(NOW).orElseThrow().getStatus()).isEqualTo(RankingBoostRun.Status.APPLIED);
    }

    @Test
    void exactLeaderDeliveryUsesExistingPipelineOutsideApplyTransactionAndIsNeverBlindlyResent() {
        agree(leader);
        identities.saveAndFlush(AuthIdentity.toss(leader, "toss-real"));
        when(smartMessages.sendMessage(anyString(), anyString(), anyString())).thenAnswer(inv -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            assertThat(runs.findByRunDate(DATE).orElseThrow().getStatus()).isEqualTo(RankingBoostRun.Status.APPLIED);
            return new TossSmartMessageClient.SendResult(true, "content-id", null, null, false, "SUCCESS", "{}");
        });
        scheduler.tick(); scheduler.tick();
        RankingBoostRun run = runs.findByRunDate(DATE).orElseThrow();
        NotificationDelivery delivery = deliveries.findById(run.getNotificationDeliveryId()).orElseThrow();
        assertThat(delivery.getUserId()).isEqualTo(leader.getId());
        assertThat(delivery.getType()).isEqualTo(NotificationType.RANK_CHANGE);
        assertThat(delivery.getStatus()).isEqualTo(NotificationDeliveryStatus.SENT);
        assertThat(delivery.getContextJson()).contains("\"currentRank\":2", "\"rankChange\":1", "\"direction\":\"DOWN\"");
        assertThat(run.getDispatchClaimedAt()).isEqualTo(NOW);
        verify(smartMessages, times(1)).sendMessage("toss-real", "RANK_SET", delivery.getContextJson());
    }

    @Test
    void killSwitchAndRevokedConsentStopUnclaimedPendingDelivery() {
        agree(leader);
        RankingBoostRun run = service.advance(NOW).orElseThrow();
        assertThat(run.getNotificationDeliveryId()).isNotNull();
        configAt = NOW.minusSeconds(30); policy(false, List.of(staff.getId()), 1000);
        assertThat(service.claimDispatch(DATE, NOW)).isEmpty();
        configAt = NOW.minusSeconds(20); policy(true, List.of(staff.getId()), 1000);
        NotificationPreference preference = preferences.findByUserIdAndType(leader.getId(), NotificationType.RANK_CHANGE).orElseThrow();
        preference.updateEnabled(false); preferences.saveAndFlush(preference);
        assertThat(service.claimDispatch(DATE, NOW)).isEmpty();
        assertThat(runs.findById(run.getId()).orElseThrow().getDispatchClaimedAt()).isNull();
        assertThat(entries.findBySeasonAndUserId(season, staff.getId()).orElseThrow().getScore()).isEqualTo(1800L);
        verifyNoInteractions(smartMessages);
    }

    @Test
    void missingEntriesTieByUuidTextAndInactiveInternalUsersAreIgnored() {
        AppUser second = users.saveAndFlush(AppUser.createActive("second staff", null));
        AppUser inactive = AppUser.createActive("suspended staff", null); inactive.suspend();
        inactive = users.saveAndFlush(inactive);
        configAt = NOW.minusSeconds(30); policy(true, List.of(second.getId(), inactive.getId(), staff.getId(), UUID.randomUUID()), 1000);
        UUID expected = java.util.stream.Stream.of(staff.getId(), second.getId()).min(Comparator.comparing(UUID::toString)).orElseThrow();
        assertThat(service.advance(NOW).orElseThrow().getSelectedUserId()).isEqualTo(expected);
        assertThat(entries.findBySeasonAndUserId(season, inactive.getId())).isEmpty();
    }

    @Test
    void alreadyAheadSkipsAndAnotherInternalAboveTheRealLeaderStopsFurtherBoosts() {
        agree(leader);
        entries.saveAndFlush(RankingEntry.createFor(season, staff, 2000L, null, NOW));
        assertThat(service.advance(NOW).orElseThrow().getSkipReason()).isEqualTo("SELECTED_ALREADY_AHEAD");
        runs.deleteAll();
        AppUser lowStaff = users.saveAndFlush(AppUser.createActive("low staff", null));
        configAt = NOW.minusSeconds(30); policy(true, List.of(staff.getId(), lowStaff.getId()), 1000);
        service.plan(NOW);
        // The real leader already sits below one internal account. Boosting another would stack
        // internal accounts above every real user while the leader is idle, until they fill the podium.
        RankingBoostRun run = service.advance(NOW).orElseThrow();
        assertThat(run.getSkipReason()).isEqualTo("REAL_LEADER_NOT_FIRST");
        assertThat(entries.findBySeasonAndUserId(season, lowStaff.getId())).isEmpty();
        assertThat(deliveries.count()).isZero();
    }

    @Test
    void noActiveInternalUserAndNoRealLeaderAreAuditedSkips() {
        staff.suspend(); users.saveAndFlush(staff);
        assertThat(service.advance(NOW).orElseThrow().getSkipReason()).isEqualTo("NO_ACTIVE_INTERNAL_USER");
        runs.deleteAll(); entries.deleteAll(); service.plan(NOW);
        assertThat(service.advance(NOW).orElseThrow().getSkipReason()).isEqualTo("NO_REAL_LEADER");
    }

    @Test
    void concurrentPlanningAndDispatchClaimsAreUniqueAndClaimedUnknownSendRequiresManualReview() throws Exception {
        runs.deleteAll();
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<?> one = executor.submit(() -> service.plan(NOW));
            Future<?> two = executor.submit(() -> service.plan(NOW));
            one.get(20, TimeUnit.SECONDS); two.get(20, TimeUnit.SECONDS);
        }
        assertThat(runs.count()).isEqualTo(1);
        agree(leader); service.advance(NOW);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<Optional<Long>> one = executor.submit(() -> service.claimDispatch(DATE, NOW));
            Future<Optional<Long>> two = executor.submit(() -> service.claimDispatch(DATE, NOW));
            assertThat(java.util.stream.Stream.of(one.get(20, TimeUnit.SECONDS), two.get(20, TimeUnit.SECONDS))
                    .filter(Optional::isPresent).count()).isEqualTo(1);
        }
        scheduler.tick();
        assertThat(service.claimDispatch(DATE, NOW)).isEmpty();
        assertThat(deliveries.findAll()).singleElement().satisfies(delivery ->
                assertThat(delivery.getStatus()).isEqualTo(NotificationDeliveryStatus.PENDING));
        verifyNoInteractions(smartMessages);
    }

    @Test
    void weeklyRolloverPreservesOldBoostForDisplayButNewSeasonStartsWithNoBoost() {
        service.advance(NOW);
        projection.syncWeeklyScore(season, staff.getId(), 25L, NOW, false);
        assertThat(entries.findBySeasonAndUserId(season, staff.getId()).orElseThrow().getScore()).isEqualTo(1825L);
        RankingSeason next = seasons.saveAndFlush(RankingSeason.activeWeekly(LocalDate.of(2026, 9, 21),
                Instant.parse("2026-09-20T15:00:00Z"), Instant.parse("2026-09-27T15:00:00Z")));
        projection.syncWeeklyScore(next, staff.getId(), 25L, Instant.parse("2026-09-21T10:00:00Z"), false);
        assertThat(entries.findBySeasonAndUserId(next, staff.getId()).orElseThrow().getScore()).isEqualTo(25L);
        assertThat(entries.findBySeasonAndUserId(next, staff.getId()).orElseThrow().getRankingBoostScore()).isZero();
        configAt = NOW.minusSeconds(30); policy(false, List.of(), 1000);
        assertThat(exclusions.excludedUserIds(season.getId(), NOW).orElseThrow()).contains(staff.getId());
        assertThat(exclusions.excludedUserIds(next.getId(), NOW).orElseThrow()).doesNotContain(staff.getId());
        jdbcTemplate.update("DELETE FROM app_config WHERE config_key = ?", SystemRankingBoostPolicy.KEY);
        assertThat(exclusions.excludedUserIds(season.getId(), NOW)).isEmpty();
    }

    @Test
    void redisFailureDoesNotRollbackAuditedBoostAndExistingReconciliationRepairsProjection() {
        doThrow(new IllegalStateException("Redis temporarily down")).when(rankingRedis)
                .updateScore(eq(season.getId()), eq(staff.getId()), anyLong(), any(), any());
        assertThat(service.advance(NOW).orElseThrow().getStatus()).isEqualTo(RankingBoostRun.Status.APPLIED);
        assertThat(entries.findBySeasonAndUserId(season, staff.getId()).orElseThrow().getScore()).isEqualTo(1800L);
        assertThat(rankingRedis.findScore(season.getId(), staff.getId())).isZero();
        doCallRealMethod().when(rankingRedis).updateScore(anyLong(), any(), anyLong(), any(), any());
        reconciliation.reconcileActiveWeekly();
        assertThat(rankingRedis.findScore(season.getId(), staff.getId())).isEqualTo(1800L);
        assertThat(runs.count()).isEqualTo(1);
    }

    @Test
    void concurrentLeaderRealScoreUpdateRollsBackStaleBoostAndRetriesSamePlanWithAccurateRanks() {
        agree(leader);
        java.util.concurrent.atomic.AtomicBoolean raced = new java.util.concurrent.atomic.AtomicBoolean();
        doAnswer(inv -> {
            Object sampled = inv.callRealMethod();
            if (raced.compareAndSet(false, true)) projection.syncWeeklyScore(season, leader.getId(), 1600L, NOW, false);
            return sampled;
        }).when(entries).findRealLeader(eq(season.getId()), anyCollection());
        Throwable conflict = catchThrowable(() -> service.advance(NOW));
        assertThat(raced).isTrue();
        assertThat(entries.findBySeasonAndUserId(season, leader.getId()).orElseThrow().getScore()).isEqualTo(1600L);
        assertThat(conflict).isInstanceOf(org.springframework.dao.OptimisticLockingFailureException.class);
        assertThat(runs.findByRunDate(DATE).orElseThrow().getStatus()).isEqualTo(RankingBoostRun.Status.PLANNED);
        assertThat(entries.findBySeasonAndUserId(season, staff.getId())).isEmpty();
        RankingBoostRun retried = service.advance(NOW).orElseThrow();
        assertThat(retried.getScheduledAt()).isEqualTo(Instant.parse("2026-09-18T09:00:00Z"));
        assertThat(retried.getTargetScore()).isEqualTo(1900L);
        assertThat(retried.getLeaderRankBefore()).isEqualTo(1L);
        assertThat(retried.getLeaderRankAfter()).isEqualTo(2L);
        assertThat(retried.getNotificationDeliveryId()).isNotNull();
    }

    @Test
    void concurrentSelectedRealProjectionConflictsThenRetryPreservesRealScore() {
        entries.saveAndFlush(RankingEntry.createFor(season, staff, 100L, null, NOW));
        doAnswer(inv -> {
            projection.syncWeeklyScore(season, staff.getId(), 200L, NOW, false);
            return 300;
        }).when(random).increment(200, 500);
        assertThatThrownBy(() -> service.advance(NOW)).isInstanceOf(org.springframework.dao.OptimisticLockingFailureException.class);
        assertThat(runs.findByRunDate(DATE).orElseThrow().getStatus()).isEqualTo(RankingBoostRun.Status.PLANNED);
        assertThat(entries.findBySeasonAndUserId(season, staff.getId()).orElseThrow().getScore()).isEqualTo(200L);
        doReturn(300).when(random).increment(200, 500);
        service.advance(NOW);
        RankingEntry retried = entries.findBySeasonAndUserId(season, staff.getId()).orElseThrow();
        assertThat(retried.getRealScore()).isEqualTo(200L);
        assertThat(retried.getScore()).isEqualTo(1800L);
        assertThat(retried.getRankingBoostScore()).isEqualTo(1600L);
        assertThat(runs.findSeasonRecipients(season.getId())).containsExactly(staff.getId());
    }

    @Test
    void actualRolloverAndBoostShareLockAndClosedSnapshotPreservesAnyCommittedBoost() throws Exception {
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            CountDownLatch start = new CountDownLatch(1);
            Future<?> apply = executor.submit(() -> { await(start); service.advance(NOW); });
            Future<?> rollover = executor.submit(() -> { await(start); seasonService.rolloverWeeklySeasons(season.getEndsAt().plusSeconds(600)); });
            start.countDown(); apply.get(30, TimeUnit.SECONDS); rollover.get(30, TimeUnit.SECONDS);
        }
        RankingSeason closed = seasons.findById(season.getId()).orElseThrow();
        assertThat(closed.getStatus()).isEqualTo(RankingSeasonStatus.CLOSED);
        RankingSeason next = seasonService.findActiveWeeklySeason().orElseThrow();
        assertThat(next.getId()).isNotEqualTo(season.getId());
        Optional<RankingEntry> historical = entries.findBySeasonAndUserId(closed, staff.getId());
        if (runs.findByRunDate(DATE).orElseThrow().getStatus() == RankingBoostRun.Status.APPLIED) {
            assertThat(historical.orElseThrow().getScore()).isEqualTo(1800L);
            assertThat(historical.orElseThrow().getFinalRank()).isEqualTo(1L);
        } else assertThat(historical).isEmpty();
        assertThat(entries.findBySeasonAndUserId(next, staff.getId())).isEmpty();
    }

    private void await(CountDownLatch start) {
        try { start.await(); } catch (InterruptedException exception) { Thread.currentThread().interrupt(); throw new IllegalStateException(exception); }
    }

    private void agree(AppUser user) {
        NotificationPreference preference = NotificationPreference.defaultOf(user.getId(), NotificationType.RANK_CHANGE);
        preference.applyAgreement("alreadyAgreed"); preferences.saveAndFlush(preference);
    }

    private void policy(boolean enabled, List<UUID> ids, int threshold) {
        String list = ids.stream().map(id -> "\"" + id + "\"").collect(java.util.stream.Collectors.joining(","));
        configs.saveAndFlush(AppConfig.createFor(SystemRankingBoostPolicy.KEY,
                "{\"enabled\":" + enabled + ",\"internalUserIds\":[" + list + "],\"minimumLeaderScore\":" + threshold
                        + ",\"minIncrement\":200,\"maxIncrement\":500}", configAt));
    }
}
