package com.ggukmoney.beanzip.domain.keycap.service;

import com.ggukmoney.beanzip.domain.keycap.entity.*;
import com.ggukmoney.beanzip.domain.keycap.passive.ClickCriticalSchedule;
import com.ggukmoney.beanzip.domain.keycap.repository.*;
import com.ggukmoney.beanzip.domain.keycap.passive.KeycapPassivePolicy;
import com.ggukmoney.beanzip.domain.point.entity.PointAccount;
import com.ggukmoney.beanzip.domain.point.repository.PointAccountRepository;
import com.ggukmoney.beanzip.domain.tap.entity.*;
import com.ggukmoney.beanzip.domain.tap.repository.*;
import com.ggukmoney.beanzip.domain.tap.dto.request.TapBatchSubmitRequest;
import com.ggukmoney.beanzip.domain.tap.dto.response.TapBatchSubmitResponse;
import com.ggukmoney.beanzip.domain.tap.service.TapBatchService;
import com.ggukmoney.beanzip.domain.user.entity.AppUser;
import com.ggukmoney.beanzip.domain.user.repository.AppUserRepository;
import com.ggukmoney.beanzip.global.config.*;
import com.ggukmoney.beanzip.global.config.entity.AppConfig;
import com.ggukmoney.beanzip.global.config.repository.AppConfigRepository;
import com.ggukmoney.beanzip.support.FullStackIntegrationTestSupport;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class KeycapPassiveIntegrationTest extends FullStackIntegrationTestSupport {
    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean AppConfigBatchLoader requestLoader;
    @Autowired KeycapPassiveService passive;
    @Autowired TapBatchService taps;
    @Autowired com.ggukmoney.beanzip.domain.tap.service.TapStatusService status;
    @Autowired KeycapService keycaps;
    @Autowired KeycapDrawService draws;
    @Autowired AppUserRepository users;
    @Autowired KeycapRepository catalog;
    @Autowired UserKeycapRepository owned;
    @Autowired PointAccountRepository accounts;
    @Autowired KeycapBoxAccountRepository wallets;
    @Autowired UserTapProgressRepository progress;
    @Autowired UserTapSessionRepository sessions;
    @Autowired UserTapDailyRepository dailies;
    @Autowired TapBatchRepository batches;
    @Autowired AppConfigRepository configs;
    @Autowired KeycapPassivePolicyConfig policy;
    @Autowired TapPolicyConfig tapPolicy;
    @Autowired KeycapPassiveCheckpointRepository checkpoints;
    @Autowired PlatformTransactionManager tx;
    @Autowired com.ggukmoney.beanzip.global.config.ops.OpsConfigService ops;
    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean com.ggukmoney.beanzip.domain.ranking.service.RankingSeasonService seasons;
    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean com.ggukmoney.beanzip.domain.user.service.UserRewardLock requestLock;
    @Autowired javax.sql.DataSource dataSource;
    @Autowired KeycapBoxPolicyConfig economy;
    @jakarta.persistence.PersistenceContext jakarta.persistence.EntityManager projectionEntityManager;
    @Autowired com.ggukmoney.beanzip.domain.ranking.service.RankingProjectionService projection;
    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean com.ggukmoney.beanzip.domain.ranking.repository.RankingEntryRepository rankingEntries;
    @MockitoBean KeycapRewardSelector selector;
    // Feature load excludes independent scheduled jobs that retain their own DB connections.
    // Their lifecycle is exercised by separate scheduler/ranking tests.
    @MockitoBean com.ggukmoney.beanzip.global.scheduler.AdvisoryLockRunner scheduledJobs;

    @BeforeEach void enable() {
        var now=Instant.now();
        configs.save(AppConfig.createFor(KeycapBoxPolicyConfig.KEY_FREE_OPEN_LIMIT,"0",now));
        configs.save(AppConfig.createFor(KeycapBoxPolicyConfig.KEY_AD_OPEN_LIMIT,"0",now));
        configs.save(AppConfig.createFor(KeycapBoxPolicyConfig.KEY_SHARD_ENABLED,"true",now));
        jdbcTemplate.update("delete from app_config where config_key=?",KeycapPassivePolicyConfig.KEY_ENABLED);
        configs.saveAll(KeycapPassivePolicyConfig.DEFAULT_VALUES.entrySet().stream().map(entry ->
                AppConfig.createFor(entry.getKey(),entry.getKey().equals(KeycapPassivePolicyConfig.KEY_ENABLED)?"true":entry.getValue(),
                        entry.getKey().equals(KeycapPassivePolicyConfig.KEY_ENABLED)?Instant.EPOCH:now)).toList());
        configs.save(AppConfig.createFor(TapPolicyConfig.KEY_RATE_LIMIT_ENABLED,"false",now));
        policy.refresh(); tapPolicy.refresh();
    }

    private AppUser user(String code) {
        var user=users.save(AppUser.createActive("passive-"+UUID.randomUUID(),null));
        accounts.save(PointAccount.createFor(user));
        var wallet=KeycapBoxAccount.createFor(user, Instant.now()); wallet.addShards(100); wallets.save(wallet);
        progress.save(UserTapProgress.createFor(user,20));
        var now=Instant.now();
        sessions.save(UserTapSession.createFor(user,now,now.plusSeconds(3600),10));
        if(code!=null) {
            var gear=UserKeycap.createOwned(user,catalog.findByCode(code).orElseThrow(),now);
            gear.equip(); owned.save(gear);
        }
        return user;
    }

    private void elapsed(AppUser user,int days) {
        passive.settle(user.getId(),"init");
        jdbcTemplate.update("update keycap_passive_checkpoint set last_activity_at=? where user_id=?",
                java.sql.Timestamp.from(Instant.now().minus(Duration.ofDays(days))),user.getId());
    }

    @Test void announcedClickCriticalsAreTheOnesTheNextBatchAppliesAndEachBatchIsAudited() {
        var user=user("pudding");
        var session=UUID.randomUUID();
        var first=taps.submitBatch(user.getId(),new TapBatchSubmitRequest(session,1L,100));
        var schedule=first.clickCriticalSchedule();
        assertThat(schedule.multiplier()).isEqualTo(2);
        assertThat(schedule.baseTapNumber()).isEqualTo(100);
        assertThat(status.getTodayStatus(user.getId()).clickCriticalSchedule()).isEqualTo(schedule);

        long sequence=2;
        for (int size : new int[]{37,100,64}) {
            var announced=schedule;
            var next=taps.submitBatch(user.getId(),new TapBatchSubmitRequest(session,sequence++,size));
            int due=(int)announced.tapOffsets().stream().filter(offset -> offset<=size).count();
            assertThat(next.effectiveCount()).as("batch of %d",size).isEqualTo(size+due);
            schedule=next.clickCriticalSchedule();
            assertThat(schedule.baseTapNumber()).isEqualTo(announced.baseTapNumber()+size);
            assertThat(schedule.tapOffsets().stream().filter(offset -> offset<=ClickCriticalSchedule.WINDOW-size).toList())
                    .isEqualTo(announced.tapOffsets().stream().filter(offset -> offset>size).map(offset -> offset-size).toList());
            var audit=jdbcTemplate.queryForMap("select start_tap_number,equipped_keycap_code,click_critical_probability,"
                    +"click_critical_multiplier,click_critical_count from tap_batch where user_id=? and sequence=?",user.getId(),sequence-1);
            assertThat(audit).containsEntry("start_tap_number",announced.baseTapNumber()+1).containsEntry("equipped_keycap_code","pudding")
                    .containsEntry("click_critical_multiplier",2).containsEntry("click_critical_count",due)
                    .containsEntry("click_critical_probability",passive.displayEffects(user.getId()).click().probability());
        }
        // A retried batch neither applies nor re-rolls anything.
        assertThat(taps.submitBatch(user.getId(),new TapBatchSubmitRequest(session,sequence-1,64)).clickCriticalSchedule())
                .isEqualTo(schedule);
        assertThat(status.getTodayStatus(user.getId()).clickCriticalSchedule()).isEqualTo(schedule);
    }

    @Test void keycapsWithoutClickCriticalAnnounceNothing() {
        var user=user("main");
        var result=taps.submitBatch(user.getId(),new TapBatchSubmitRequest(UUID.randomUUID(),1L,100));
        assertThat(result.clickCriticalSchedule()).isNull();
        assertThat(result.effectiveCount()).isEqualTo(100);
        assertThat(status.getTodayStatus(user.getId()).clickCriticalSchedule()).isNull();
        assertThat(jdbcTemplate.queryForMap("select equipped_keycap_code,click_critical_count from tap_batch where user_id=?",user.getId()))
                .containsEntry("equipped_keycap_code","main").containsEntry("click_critical_count",0);
    }

    @Test void sameKeyAndSimultaneousRequestsPayExactlyOnceAndReplaySnapshot() throws Exception {
        seasons.ensureCurrentWeeklySeason(Instant.now());
        var user=user("cheer"); elapsed(user,1);
        var result=concurrently(() -> passive.settle(user.getId(),"same"),() -> passive.settle(user.getId(),"same"));
        assertThat(result.get(0)).isEqualTo(result.get(1));
        assertThat(((com.ggukmoney.beanzip.domain.keycap.dto.response.KeycapPassiveSettleResponse)result.get(0)).autoClicksGranted()).isEqualTo(60);
        assertThat(jdbcTemplate.queryForObject("select count(*) from keycap_passive_settlement where user_id=?",Long.class,user.getId())).isEqualTo(2);
        var today=dailies.findByUserIdAndTapDate(user.getId(),LocalDate.now(ZoneId.of("Asia/Seoul"))).orElseThrow();
        assertThat(today.getTotalValidTapCount()).isZero();
        assertThat(today.getValidTapCount()).isEqualTo(60);
        assertThat(today.getTotalEffectiveTapCount()).isEqualTo(60);
        assertThat(progress.findByUserId(user.getId()).orElseThrow().getCumulativeMissionTapCount()).isZero();
        assertThat(progress.findByUserId(user.getId()).orElseThrow().getCumulativeRankingTapCount()).isEqualTo(60);
        assertThat(jdbcTemplate.queryForList(
                "select e.score-e.ranking_boost_score from ranking_entry e join ranking_season s on e.season_id=s.id where e.user_id=? and s.ranking_type in ('WEEKLY','ALL_TIME')",
                Long.class,user.getId())).containsExactlyInAnyOrder(60L,60L);
    }

    @Test void concurrentTapBatchesPersistOneOutcomeAndAutomaticClicksTakeAllowanceFirst() throws Exception {
        var user=user("cheer"); elapsed(user,1);
        var day=UserTapDaily.createFor(user,LocalDate.now(ZoneId.of("Asia/Seoul"))); day.addValidTaps(2990); dailies.save(day);
        var request=new TapBatchSubmitRequest(UUID.randomUUID(),1L,50);
        var results=concurrently(() -> taps.submitBatch(user.getId(),request),() -> taps.submitBatch(user.getId(),request));
        var responses=results.stream().map(TapBatchSubmitResponse.class::cast).toList();
        assertThat(responses).extracting(TapBatchSubmitResponse::autoClicksGranted).containsExactlyInAnyOrder(60,0);
        assertThat(responses).extracting(TapBatchSubmitResponse::effectiveCount).containsExactlyInAnyOrder(50,0);
        assertThat(responses).allSatisfy(response -> {
            assertThat(response.acceptedCount()).isEqualTo(50);
            assertThat(response.effectiveTapCountToday()).isEqualTo(110);
        });
        assertThat(progress.findByUserId(user.getId()).orElseThrow().getCumulativeMissionTapCount()).isEqualTo(50);
        assertThat(dailies.findByUserIdAndTapDate(user.getId(),day.getTapDate()).orElseThrow().getValidTapCount()).isEqualTo(3000);
        var replay=taps.submitBatch(user.getId(),new TapBatchSubmitRequest(request.tapSessionId(),1L,51));
        assertThat(replay.acceptedCount()).isEqualTo(50);
        assertThat(replay.pointsAwarded()).isZero();
        assertThat(replay.shardsDropped()).isZero();
        assertThat(replay.autoClicksGranted()).isZero();
        assertThat(progress.findByUserId(user.getId()).orElseThrow().getCumulativeRankingTapCount()).isEqualTo(110);
        assertThat(jdbcTemplate.queryForObject("select count(*) from tap_batch where user_id=?",Long.class,user.getId())).isEqualTo(1);
    }

    @Test void positiveAutomaticSettlementProjectsUnconvertedLegacyProgress() {
        seasons.ensureCurrentWeeklySeason(Instant.now());
        var user=user("cheer"); elapsed(user,1);
        var pending=UserKeycap.createInProgress(user,catalog.findByCode("pinkjelly").orElseThrow());
        pending.addShard(3,Instant.now()); owned.saveAndFlush(pending);
        var result=passive.settle(user.getId(),"legacy-positive");
        assertThat(result.autoClicksGranted()).isEqualTo(60);
        // Existing100 + legacy3 + one base drop, paced from the automatic batch end.
        assertThat(result.shardsDropped()).isEqualTo(1);
        assertThat(result.shardBalance()).isEqualTo(104);
        assertThat(pendingShardCount(user.getId())).isEqualTo(3);
        assertThat(passive.settle(user.getId(),"legacy-positive")).isEqualTo(result);
        assertThat(wallets.findByUserId(user.getId()).orElseThrow().getShardBalance()).isEqualTo(101);
    }

    @Test void preRolloutTapRetryProjectsLegacyProgressWithoutAnotherAward() {
        var user=user(null);
        var pending=UserKeycap.createInProgress(user,catalog.findByCode("pinkjelly").orElseThrow());
        pending.addShard(3,Instant.now()); owned.saveAndFlush(pending);
        var sessionId=UUID.randomUUID();
        var saved=TapBatch.createFor(user,sessionId,1L,42,"legacy-hash"); saved.markAccepted(42);
        batches.saveAndFlush(saved);
        var result=taps.submitBatch(user.getId(),new TapBatchSubmitRequest(sessionId,1L,99));
        assertThat(result.pointsAwarded()).isZero();
        assertThat(result.autoClicksGranted()).isZero();
        assertThat(result.shardsDropped()).isZero();
        assertThat(result.shardBalance()).isEqualTo(103);
        assertThat(pendingShardCount(user.getId())).isEqualTo(3);
        assertThat(taps.submitBatch(user.getId(),new TapBatchSubmitRequest(sessionId,1L,5)).shardBalance()).isEqualTo(103);
    }

    @Test void disabledPolicyPaysPreviouslyEarnedClicksAndExcludesAllInactiveTime() {
        var user=user("cheer"); elapsed(user,7);
        var start=Instant.now().minus(Duration.ofDays(7)).truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        activationHistory(start,false);
        jdbcTemplate.update("update keycap_passive_checkpoint set last_activity_at=? where user_id=?",
                java.sql.Timestamp.from(start),user.getId());
        assertThat(passive.status(user.getId()).pendingAutoClicks()).isEqualTo(180);
        assertThat(passive.settle(user.getId(),"closed-periods").autoClicksGranted()).isEqualTo(180);
        assertThat(passive.settle(user.getId(),"still-disabled").autoClicksGranted()).isZero();
        assertThat(policy.settlementSnapshot(Instant.now()).enabled()).isFalse();
    }

    @Test void repeatedOnOffTransitionsDoNotEraseEarlierUnclaimedPeriods() {
        var user=user("cheer"); elapsed(user,7);
        var start=Instant.now().minus(Duration.ofDays(7)).truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        activationHistory(start,true);
        jdbcTemplate.update("update keycap_passive_checkpoint set last_activity_at=? where user_id=?",
                java.sql.Timestamp.from(start),user.getId());
        // Another pod's cache still has the pre-toggle policy; settlement must read committed history.
        assertThat(passive.settle(user.getId(),"multiple-periods").autoClicksGranted()).isEqualTo(240);
        assertThat(progress.findByUserId(user.getId()).orElseThrow().getCumulativeRankingTapCount()).isEqualTo(240);
        assertThat(progress.findByUserId(user.getId()).orElseThrow().getCumulativeMissionTapCount()).isZero();
        assertThat(policy.settlementSnapshot(Instant.now()).activePeriods()).hasSize(3);
    }

    private void activationHistory(Instant start,boolean reopen) {
        jdbcTemplate.update("delete from app_config where config_key=?",KeycapPassivePolicyConfig.KEY_ENABLED);
        configs.saveAll(List.of(AppConfig.createFor(KeycapPassivePolicyConfig.KEY_ENABLED,"true",start),
                AppConfig.createFor(KeycapPassivePolicyConfig.KEY_ENABLED,"false",start.plus(Duration.ofDays(2))),
                AppConfig.createFor(KeycapPassivePolicyConfig.KEY_ENABLED,"true",start.plus(Duration.ofDays(4))),
                AppConfig.createFor(KeycapPassivePolicyConfig.KEY_ENABLED,"false",start.plus(Duration.ofDays(5)))));
        if (reopen) configs.save(AppConfig.createFor(KeycapPassivePolicyConfig.KEY_ENABLED,"true",start.plus(Duration.ofDays(6))));
    }

    @Test void equipmentChangeClosesPreviousRateAndLevelUpStoresNewRate() {
        var user=user("cheer"); elapsed(user,1);
        var pink=catalog.findByCode("pinkjelly").orElseThrow();
        owned.save(UserKeycap.createOwned(user,pink,Instant.now()));
        keycaps.equipKeycap(user.getId(),pink.getPublicId());
        assertThat(checkpoints.findById(user.getId()).orElseThrow().getClicksPerDay()).isEqualTo(100);
        assertThat(progress.findByUserId(user.getId()).orElseThrow().getCumulativeRankingTapCount()).isEqualTo(60);
        when(selector.select(any())).thenReturn(pink);
        draws.draw(user.getId(),"upgrade");
        var checkpoint=checkpoints.findById(user.getId()).orElseThrow();
        assertThat(checkpoint.getEquippedLevel()).isEqualTo(2);
        assertThat(checkpoint.getClicksPerDay()).isEqualTo(120);
    }

    @Test void tapDrawEquipRaceKeepsWalletAndCheckpointConsistent() throws Exception {
        var user=user("cheer"); elapsed(user,1);
        var pink=catalog.findByCode("pinkjelly").orElseThrow();
        owned.save(UserKeycap.createOwned(user,pink,Instant.now()));
        when(selector.select(any())).thenReturn(pink);
        concurrently(() -> taps.submitBatch(user.getId(),new TapBatchSubmitRequest(UUID.randomUUID(),1L,30)),
                () -> { keycaps.equipKeycap(user.getId(),pink.getPublicId()); return draws.draw(user.getId(),"race"); });
        assertThat(progress.findByUserId(user.getId()).orElseThrow().getCumulativeRankingTapCount()).isEqualTo(90);
        assertThat(checkpoints.findById(user.getId()).orElseThrow().getClicksPerDay()).isEqualTo(120);
        assertThat(wallets.findByUserId(user.getId()).orElseThrow().getShardBalance()).isGreaterThanOrEqualTo(95);
    }

    @Test void outerFailureRollsBackPayoutCheckpointAndIdempotencyRecord() {
        var user=user("cheer"); elapsed(user,1);
        var original=checkpoints.findById(user.getId()).orElseThrow().getLastActivityAt();
        assertThatThrownBy(() -> new TransactionTemplate(tx).execute(status -> {
            passive.settle(user.getId(),"rollback");
            throw new IllegalStateException("after payout");
        })).hasMessageContaining("after payout");
        assertThat(checkpoints.findById(user.getId()).orElseThrow().getLastActivityAt()).isEqualTo(original);
        assertThat(accounts.findByUserId(user.getId()).orElseThrow().getBalance()).isZero();
        assertThat(jdbcTemplate.queryForObject("select count(*) from keycap_passive_settlement where user_id=? and idempotency_key='api:rollback'",Long.class,user.getId())).isZero();
        assertThat(passive.settle(user.getId(),"rollback").autoClicksGranted()).isEqualTo(60);
    }

    @Test void disabledUnownedAndUnequippedApisAreSafeAndSettleRequiresKey() throws Exception {
        var user=user(null);
        var token=saveTokenBackedSession(user.getId(),"passive-api").accessToken();
        mockMvc.perform(get("/api/keycaps/passive").header("Authorization","Bearer "+token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.effects.capLevel").doesNotExist())
                .andExpect(jsonPath("$.data.effects.bonuses.rankingPercent").value(0.0))
                .andExpect(jsonPath("$.data.pendingAutoClicks").value(0));
        mockMvc.perform(post("/api/keycaps/passive/settle").header("Authorization","Bearer "+token))
                .andExpect(status().isBadRequest());
        assertThat(keycaps.getKeycaps().keycaps()).allSatisfy(card -> assertThat(card.previewEffects().previewLevel()).isEqualTo(1));
        assertThat(keycaps.getKeycaps().keycaps()).anySatisfy(card -> assertThat(card.previewEffects().bonuses().rankingPercent()).isPositive());
        configs.save(AppConfig.createFor(KeycapPassivePolicyConfig.KEY_ENABLED,"false",Instant.now()));
        policy.refresh();
        assertThat(policy.snapshot().enabled()).isFalse();
        assertThat(passive.settle(user.getId(),"disabled").autoClicksGranted()).isZero();
        assertThat(passive.status(user.getId()).enabled()).isFalse();
    }

    @Test void activatingOpsSwitchAtomicallyRecordsANewCutoff() {
        configs.save(AppConfig.createFor(KeycapPassivePolicyConfig.KEY_ENABLED,"false",Instant.now()));
        policy.refresh();
        assertThat(policy.snapshot().enabled()).isFalse();
        var current=ops.current(KeycapPassivePolicyConfig.KEY_ENABLED);
        var before=Instant.now();
        ops.change(KeycapPassivePolicyConfig.KEY_ENABLED,current.getPublicId().toString(),"true","integration","activate");
        assertThat(policy.snapshot().enabled()).isTrue();
        assertThat(policy.snapshot().enabledAt()).isBetween(before,Instant.now());
        assertThat(ops.current(KeycapPassivePolicyConfig.KEY_ENABLED_AT).getEffectiveAt())
                .isEqualTo(ops.current(KeycapPassivePolicyConfig.KEY_ENABLED).getEffectiveAt());
    }

    @Test void simultaneousIndividuallyValidPolicyChangesCannotExceedCombinedBudget() throws Exception {
        String shard="keycap.passive.earth.shard.probability";
        String click="keycap.passive.earth.click.probability";
        configs.saveAll(List.of(AppConfig.createFor(shard,"0.10",Instant.now()),AppConfig.createFor(click,"0.05",Instant.now())));
        String shardBase=ops.current(shard).getPublicId().toString();
        String clickBase=ops.current(click).getPublicId().toString();
        var results=concurrently(() -> changePolicy(shard,shardBase,"0.15"),() -> changePolicy(click,clickBase,"0.10"));
        assertThat(results).containsExactlyInAnyOrder("saved","rejected");
        var values=new HashMap<String,String>();
        ops.currentValues().forEach(row -> values.put(row.getConfigKey(),row.getConfigValue()));
        assertThatCode(() -> KeycapPassivePolicyConfig.decode(values)).doesNotThrowAnyException();
    }

    private String changePolicy(String key,String base,String next) {
        try { ops.change(key,base,next,"integration","budget race"); return "saved"; }
        catch (IllegalArgumentException expected) { return "rejected"; }
    }

    private List<Object> concurrently(Callable<?> a,Callable<?> b) throws Exception {
        var ready=new CountDownLatch(2); var start=new CountDownLatch(1);
        try(var executor=Executors.newFixedThreadPool(2)) {
            var futures=List.of(executor.submit(() -> {ready.countDown();start.await();return a.call();}),
                    executor.submit(() -> {ready.countDown();start.await();return b.call();}));
            assertThat(ready.await(5,TimeUnit.SECONDS)).isTrue(); start.countDown();
            return List.of(futures.get(0).get(30,TimeUnit.SECONDS),futures.get(1).get(30,TimeUnit.SECONDS));
        }
    }
    @Test void legacySettlementDoesNotReserveKeyAndSameKeyWorksAfterCutover() {
        jdbcTemplate.update("delete from app_config where config_key=?",KeycapBoxPolicyConfig.KEY_SHARD_ENABLED);
        configs.save(AppConfig.createFor(KeycapBoxPolicyConfig.KEY_SHARD_ENABLED,"false",Instant.EPOCH));
        var user=user("cheer");
        assertThat(passive.settle(user.getId(),"cross-phase").autoClicksGranted()).isZero();
        assertThat(checkpoints.findById(user.getId())).isEmpty();
        assertThat(jdbcTemplate.queryForObject("select count(*) from keycap_passive_settlement where user_id=?",Long.class,user.getId())).isZero();
        configs.save(AppConfig.createFor(KeycapBoxPolicyConfig.KEY_SHARD_ENABLED,"true",Instant.now()));
        var first=passive.settle(user.getId(),"cross-phase");
        assertThat(checkpoints.findById(user.getId())).isPresent();
        assertThat(passive.settle(user.getId(),"cross-phase")).isEqualTo(first);
        assertThat(jdbcTemplate.queryForObject("select count(*) from keycap_passive_settlement where user_id=?",Long.class,user.getId())).isEqualTo(1);
    }

    @Test void sameUserTapDrawEquipAndSettleSerializeAndKeepCheckpointConsistent() throws Exception {
        try(var rankingErrors=new RankingErrorCapture()) {
        seasons.ensureCurrentWeeklySeason(Instant.now());
        var user=user("cheer");
        var selected=catalog.findByCode("main").orElseThrow();
        var pending=UserKeycap.createInProgress(user,selected); pending.addShard(3,Instant.EPOCH); owned.save(pending);
        var gearId=catalog.findByCode("cheer").orElseThrow().getPublicId();
        elapsed(user,1);
        assertThat(wallets.findAvailableShardBalance(user.getId()).orElseThrow()).isEqualTo(103L);
        org.mockito.Mockito.doReturn(selected).when(selector).select(org.mockito.ArgumentMatchers.anyList());
        var ready=new CountDownLatch(4); var start=new CountDownLatch(1);
        List<Callable<?>> work=List.of(
                ()->taps.submitBatch(user.getId(),new TapBatchSubmitRequest(UUID.randomUUID(),1L,25)),
                ()->draws.draw(user.getId(),"mixed-draw"),
                ()->keycaps.equipKeycap(user.getId(),gearId),
                ()->passive.settle(user.getId(),"mixed-settle"));
        try(var executor=Executors.newFixedThreadPool(4)) {
            var futures=work.stream().map(call->executor.submit(()->{ready.countDown();start.await();return call.call();})).toList();
            assertThat(ready.await(5,TimeUnit.SECONDS)).isTrue();start.countDown();
            for(var future:futures) assertThat(future.get(30,TimeUnit.SECONDS)).isNotNull();
        }
        assertThat(pendingShardCount(user.getId())).isZero();
        assertThat(owned.findByUserIdWithKeycapOrderByKeycapSortOrderAscCodeAsc(user.getId()).stream().filter(k->k.getKeycap().getId().equals(selected.getId())).findFirst().orElseThrow().getLevel()).isEqualTo(1);
        var checkpoint=checkpoints.findById(user.getId()).orElseThrow();
        assertThat(checkpoint.getEquippedKeycapCode()).isEqualTo("cheer");
        assertThat(owned.findByUserIdWithKeycapOrderByKeycapSortOrderAscCodeAsc(user.getId()).stream().filter(UserKeycap::isEquipped).findFirst().orElseThrow().getKeycap().getCode()).isEqualTo("cheer");
        assertThat(checkpoint.getClicksPerDay()).isEqualTo(60);
        assertThat(checkpoint.getRemainderNumerator()).isBetween(0L,86_399_999_999_999L);
        var today=dailies.findByUserIdAndTapDate(user.getId(),LocalDate.now(ZoneId.of("Asia/Seoul"))).orElseThrow();
        assertThat(progress.findByUserId(user.getId()).orElseThrow().getCumulativeMissionTapCount()).isEqualTo(25);
        assertThat(accounts.findByUserId(user.getId()).orElseThrow().getBalance()).isEqualTo((long)today.getPointEarnedAmount());
        assertThat(wallets.findByUserId(user.getId()).orElseThrow().getShardBalance()).isEqualTo(100);
        assertThat(today.getTotalEffectiveTapCount()).isEqualTo(85);
        var scores=jdbcTemplate.queryForList("select e.score-coalesce(e.ranking_boost_score,0) from ranking_entry e join ranking_season s on s.id=e.season_id where e.user_id=? and s.status='ACTIVE' and s.ranking_type in ('ALL_TIME','WEEKLY')",Long.class,user.getId());
        assertThat(scores).hasSize(2).allMatch(score->score==85L);
        assertThat(passive.settle(user.getId(),"mixed-settle")).isNotNull();
        rankingErrors.assertNone();
        }
    }

    @Test void localLoad32Users20Batches8WorkersHasOnePhaseAndPassiveReadPerBatch() throws Exception {
        try(var rankingErrors=new RankingErrorCapture()) {
        // Steady-state feature load; cold initialization is tested separately with the same pool of ten.
        seasons.ensureCurrentWeeklySeason(Instant.now());
        seasons.getOrCreateActiveAllTimeSeason();
        var usersUnderLoad=new ArrayList<AppUser>();for(int i=0;i<32;i++)usersUnderLoad.add(user(null));
        var economicReads=new java.util.concurrent.atomic.LongAdder();var passiveReads=new java.util.concurrent.atomic.LongAdder();
        org.mockito.Mockito.doAnswer(invocation->{
            if(Thread.currentThread().getName().startsWith("local-load-")) {
                String historyKey=invocation.getArgument(1);
                if(KeycapBoxPolicyConfig.KEY_SHARD_ENABLED.equals(historyKey))economicReads.increment();
                if(KeycapPassivePolicyConfig.KEY_ENABLED.equals(historyKey))passiveReads.increment();
            }
            return invocation.callRealMethod();
        }).when(requestLoader).loadWithHistory(any(),any(),any());
        var durations=new java.util.concurrent.ConcurrentLinkedQueue<Long>();var threadIds=new java.util.concurrent.atomic.AtomicInteger();
        try(var executor=Executors.newFixedThreadPool(8,job->new Thread(job,"local-load-"+threadIds.incrementAndGet()))) {
            var futures=usersUnderLoad.stream().map(user->executor.submit(()->{
                UUID session=UUID.randomUUID();
                for(long sequence=1;sequence<=20;sequence++) {
                    long before=System.nanoTime();
                    var response=taps.submitBatch(user.getId(),new TapBatchSubmitRequest(session,sequence,1));
                    durations.add(System.nanoTime()-before);
                    assertThat(response.effectiveCount()).isEqualTo(1);
                }
                return true;
            })).toList();
            for(var future:futures)assertThat(future.get(90,TimeUnit.SECONDS)).isTrue();
        }
        assertThat(economicReads.sum()).isEqualTo(640);assertThat(passiveReads.sum()).isEqualTo(640);
        for(var user:usersUnderLoad) {
            var totals=progress.findByUserId(user.getId()).orElseThrow();
            assertThat(totals.getCumulativeMissionTapCount()).isEqualTo(20);
            assertThat(totals.getCumulativeRankingTapCount()).isEqualTo(20);
            assertThat(wallets.findByUserId(user.getId()).orElseThrow().getShardBalance()).isEqualTo(101);
            assertThat(accounts.findByUserId(user.getId()).orElseThrow().getBalance()).isEqualTo(1);
            var actualScores=jdbcTemplate.queryForList("select e.score-coalesce(e.ranking_boost_score,0) from ranking_entry e join ranking_season s on s.id=e.season_id where e.user_id=? and s.status='ACTIVE' and s.ranking_type in ('ALL_TIME','WEEKLY')",Long.class,user.getId());
            assertThat(actualScores).hasSize(2).allMatch(score->score==20L);
        }
        var sorted=durations.stream().sorted().toList();
        System.out.printf("LOCAL_LOAD users=32 batches=640 workers=8 p50Ms=%.3f p95Ms=%.3f p99Ms=%.3f maxMs=%.3f economicReads=%d passiveReads=%d%n",
                sorted.get(320)/1e6,sorted.get(608)/1e6,sorted.get(633)/1e6,sorted.get(639)/1e6,economicReads.sum(),passiveReads.sum());
        rankingErrors.assertNone();
        }
    }
    @Test void exhaustedDailyPointCapPreservesTargetForFirstBatchOfNextDay() {
        var user=user(null); var date=LocalDate.now(ZoneId.of("Asia/Seoul"));
        var daily=UserTapDaily.createFor(user,date);daily.addPointEarned(150);dailies.save(daily);
        var session=UUID.randomUUID();
        assertThat(taps.submitBatch(user.getId(),new TapBatchSubmitRequest(session,1L,20)).pointsAwarded()).isZero();
        assertThat(progress.findByUserId(user.getId()).orElseThrow().getNextPointTarget()).isEqualTo(20);
        jdbcTemplate.update("update user_tap_daily set tap_date=? where user_id=? and tap_date=?",date.minusDays(1),user.getId(),date);
        assertThat(taps.submitBatch(user.getId(),new TapBatchSubmitRequest(session,2L,1)).pointsAwarded()).isEqualTo(1);
        assertThat(progress.findByUserId(user.getId()).orElseThrow().getNextPointTarget()).isGreaterThan(21);
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {false,true})
    void hundredTapBatchKeeps329DropPacingInLegacyAndShardEconomies(boolean shard) {
        seasons.ensureCurrentWeeklySeason(Instant.now());
        seasons.getOrCreateActiveAllTimeSeason();
        jdbcTemplate.update("delete from app_config where config_key=?",KeycapBoxPolicyConfig.KEY_SHARD_ENABLED);
        configs.save(AppConfig.createFor(KeycapBoxPolicyConfig.KEY_SHARD_ENABLED,Boolean.toString(shard),Instant.EPOCH));
        var now=Instant.now();
        configs.save(AppConfig.createFor(TapPolicyConfig.KEY_BOX_SESSION_STEP_1,"25",now));
        configs.save(AppConfig.createFor(TapPolicyConfig.KEY_BOX_SESSION_STEP_2,"35",now));
        configs.save(AppConfig.createFor(TapPolicyConfig.KEY_BOX_SESSION_STEP_3,"50",now));
        tapPolicy.refresh(); economy.refresh();
        var user=user(null);
        jdbcTemplate.update("update user_tap_session set next_box_target=25 where user_id=?",user.getId());

        var result=taps.submitBatch(user.getId(),new TapBatchSubmitRequest(UUID.randomUUID(),1L,100));

        assertThat(result.effectiveCount()).isEqualTo(100);
        assertThat(result.boxesDropped()).isEqualTo(shard?0:1);
        assertThat(result.shardsDropped()).isEqualTo(shard?1:0);
        assertThat(result.pointsAwarded()).isEqualTo(1);
        assertThat(wallets.findByUserId(user.getId()).orElseThrow().getBoxBalance()).isEqualTo(101);
        assertThat(sessions.findByUserId(user.getId()).orElseThrow().getNextBoxTarget()).isEqualTo(125);
    }

    @Test void coldStartAllTimeSeasonWithTenConnectionsCompletes32UserLoadWithoutRankingErrors() throws Exception {
        seasons.ensureCurrentWeeklySeason(Instant.now());
        jdbcTemplate.update("delete from ranking_entry where season_id in (select id from ranking_season where ranking_type='ALL_TIME')");
        jdbcTemplate.update("delete from ranking_season where ranking_type='ALL_TIME'");
        var pool=dataSource.unwrap(com.zaxxer.hikari.HikariDataSource.class);
        assertThat(pool.getMaximumPoolSize()).isEqualTo(10);
        long originalTimeout=pool.getConnectionTimeout();
        pool.setConnectionTimeout(3000);
        var log=(ch.qos.logback.classic.Logger)org.slf4j.LoggerFactory.getLogger(
                com.ggukmoney.beanzip.domain.ranking.service.RankingScoreSyncRequestedListener.class);
        var errors=new ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();
        errors.start();log.addAppender(errors);
        try {
            var usersUnderLoad=new ArrayList<AppUser>();for(int i=0;i<32;i++)usersUnderLoad.add(user(null));
            var ready=new CountDownLatch(8);var firstTransactions=new java.util.concurrent.atomic.AtomicInteger();
            doAnswer(invocation->{
                Object value=invocation.callRealMethod();
                if(Thread.currentThread().getName().startsWith("cold-load-") && firstTransactions.getAndIncrement()<8) {
                    ready.countDown();assertThat(ready.await(10,TimeUnit.SECONDS)).isTrue();
                }
                return value;
            }).when(org.springframework.test.util.AopTestUtils.<com.ggukmoney.beanzip.domain.user.service.UserRewardLock>getUltimateTargetObject(requestLock)).acquire(any());
            // Hold the first two empty-season projection reads together: in the broken version
            // they already own two extra connections above the eight committed tap transactions.
            var emptyProjectionReads=new CountDownLatch(2);
            doAnswer(invocation->{
                Object value=invocation.callRealMethod();
                String transaction=org.springframework.transaction.support.TransactionSynchronizationManager.getCurrentTransactionName();
                if(Thread.currentThread().getName().startsWith("cold-load-")
                        && ((Optional<?>)value).isEmpty() && transaction!=null && transaction.endsWith(".syncLatestAllTimeScore")) {
                    emptyProjectionReads.countDown();assertThat(emptyProjectionReads.await(10,TimeUnit.SECONDS)).isTrue();
                }
                return value;
            }).when(org.springframework.test.util.AopTestUtils.<com.ggukmoney.beanzip.domain.ranking.service.RankingSeasonService>getUltimateTargetObject(seasons)).findActiveAllTimeSeason();
            var economicReads=new java.util.concurrent.atomic.LongAdder();var passiveReads=new java.util.concurrent.atomic.LongAdder();
            doAnswer(invocation->{
                if(Thread.currentThread().getName().startsWith("cold-load-")) {
                    String key=invocation.getArgument(1);
                    if(KeycapBoxPolicyConfig.KEY_SHARD_ENABLED.equals(key))economicReads.increment();
                    if(KeycapPassivePolicyConfig.KEY_ENABLED.equals(key))passiveReads.increment();
                }
                return invocation.callRealMethod();
            }).when(requestLoader).loadWithHistory(any(),any(),any());
            var durations=new java.util.concurrent.ConcurrentLinkedQueue<Long>();var threadIds=new java.util.concurrent.atomic.AtomicInteger();
            try(var executor=Executors.newFixedThreadPool(8,job->new Thread(job,"cold-load-"+threadIds.incrementAndGet()))) {
                var futures=usersUnderLoad.stream().map(user->executor.submit(()->{
                    UUID session=UUID.randomUUID();
                    for(long sequence=1;sequence<=20;sequence++) {
                        long before=System.nanoTime();
                        var result=taps.submitBatch(user.getId(),new TapBatchSubmitRequest(session,sequence,1));
                        durations.add(System.nanoTime()-before);
                        assertThat(result.effectiveCount()).isEqualTo(1);
                    }
                    return true;
                })).toList();
                for(var future:futures)assertThat(future.get(90,TimeUnit.SECONDS)).isTrue();
            }
            assertThat(errors.list).filteredOn(event->event.getLevel().isGreaterOrEqual(ch.qos.logback.classic.Level.ERROR))
                    .as("cold startup must not swallow ranking synchronization failures").isEmpty();
            assertThat(economicReads.sum()).isEqualTo(640);assertThat(passiveReads.sum()).isEqualTo(640);
            assertThat(jdbcTemplate.queryForObject("select count(*) from ranking_season where ranking_type='ALL_TIME' and status='ACTIVE'",Long.class)).isEqualTo(1);
            for(var user:usersUnderLoad) {
                var totals=progress.findByUserId(user.getId()).orElseThrow();
                assertThat(totals.getCumulativeMissionTapCount()).isEqualTo(20);
                assertThat(totals.getCumulativeRankingTapCount()).isEqualTo(20);
                assertThat(wallets.findByUserId(user.getId()).orElseThrow().getShardBalance()).isEqualTo(101);
                assertThat(accounts.findByUserId(user.getId()).orElseThrow().getBalance()).isEqualTo(1);
                var scores=jdbcTemplate.queryForList("select e.score-coalesce(e.ranking_boost_score,0) from ranking_entry e join ranking_season s on s.id=e.season_id where e.user_id=? and s.status='ACTIVE' and s.ranking_type in ('ALL_TIME','WEEKLY')",Long.class,user.getId());
                assertThat(scores).hasSize(2).allMatch(score->score==20L);
            }
            var sorted=durations.stream().sorted().toList();
            System.out.printf("COLD_LOCAL_LOAD users=32 batches=640 workers=8 pool=10 rankingErrors=0 p50Ms=%.3f p95Ms=%.3f p99Ms=%.3f maxMs=%.3f economicReads=%d passiveReads=%d%n",
                    sorted.get(320)/1e6,sorted.get(608)/1e6,sorted.get(633)/1e6,sorted.get(639)/1e6,economicReads.sum(),passiveReads.sum());
        } finally {
            log.detachAppender(errors);errors.stop();pool.setConnectionTimeout(originalTimeout);
        }
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans={false,true})
    void sameUserConcurrentAllTimeProjectionsNeverLoseTheHigherCommittedScore(boolean existingEntry) throws Exception {
        seasons.getOrCreateActiveAllTimeSeason();
        var user=user(null); if(existingEntry) projection.syncAllTimeScore(user.getId(),0);
        var lowRead=new CountDownLatch(1);var releaseLow=new CountDownLatch(1);
        var highRead=new CountDownLatch(1);var lowCommitted=new CountDownLatch(1);
        doAnswer(invocation->{
            var season=invocation.getArgument(0,com.ggukmoney.beanzip.domain.ranking.entity.RankingSeason.class);
            Object value=projectionEntityManager.createQuery(
                    "select e from RankingEntry e where e.season = :season and e.user.id = :userId",
                    com.ggukmoney.beanzip.domain.ranking.entity.RankingEntry.class)
                    .setParameter("season",season).setParameter("userId",user.getId()).getResultList().stream().findFirst();
            if(Thread.currentThread().getName().equals("projection-low")) {
                lowRead.countDown();assertThat(releaseLow.await(10,TimeUnit.SECONDS)).isTrue();
            } else if(Thread.currentThread().getName().equals("projection-high")) {
                highRead.countDown();assertThat(lowCommitted.await(10,TimeUnit.SECONDS)).isTrue();
            }
            return value;
        }).when(rankingEntries).findBySeasonAndUserId(any(),eq(user.getId()));
        try(var executor=Executors.newFixedThreadPool(2)) {
            var low=executor.submit(()->{
                Thread.currentThread().setName("projection-low");
                try {return projection.syncAllTimeScore(user.getId(),20);} finally {lowCommitted.countDown();}
            });
            if (!lowRead.await(10,TimeUnit.SECONDS)) low.get(1,TimeUnit.SECONDS);
            assertThat(lowRead.getCount()).isZero();
            var high=executor.submit(()->{
                Thread.currentThread().setName("projection-high");
                return projection.syncAllTimeScore(user.getId(),40);
            });
            // Give the second transaction time to read the old entry in the broken version.
            // With user serialization it stays before the entry read until the first commit.
            highRead.await(2,TimeUnit.SECONDS);releaseLow.countDown();
            assertThat(low.get(15,TimeUnit.SECONDS).getRealScore()).isEqualTo(20);
            assertThat(high.get(15,TimeUnit.SECONDS).getRealScore()).isEqualTo(40);
        } finally {releaseLow.countDown();lowCommitted.countDown();}
        assertThat(jdbcTemplate.queryForObject("select e.score from ranking_entry e join ranking_season s on s.id=e.season_id where e.user_id=? and s.ranking_type='ALL_TIME'",Long.class,user.getId())).isEqualTo(40);
    }

    private static final class RankingErrorCapture implements AutoCloseable {
        private final ch.qos.logback.classic.Logger log=(ch.qos.logback.classic.Logger)org.slf4j.LoggerFactory.getLogger(
                com.ggukmoney.beanzip.domain.ranking.service.RankingScoreSyncRequestedListener.class);
        private final ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent> events=new ch.qos.logback.core.read.ListAppender<>();
        RankingErrorCapture() {events.start();log.addAppender(events);}
        void assertNone() {
            assertThat(events.list).filteredOn(event->event.getLevel().isGreaterOrEqual(ch.qos.logback.classic.Level.ERROR))
                    .as("ranking synchronization errors must not be swallowed").isEmpty();
        }
        @Override public void close() {log.detachAppender(events);events.stop();}
    }

    private long pendingShardCount(UUID userId) {
        return jdbcTemplate.queryForObject(
                "select coalesce(sum(shard_count), 0) from user_keycap where user_id = ? and status = 'IN_PROGRESS'",
                Long.class, userId);
    }

}
