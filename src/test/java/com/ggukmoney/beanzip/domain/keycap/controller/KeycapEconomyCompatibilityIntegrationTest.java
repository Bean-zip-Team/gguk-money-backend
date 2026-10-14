package com.ggukmoney.beanzip.domain.keycap.controller;

import com.ggukmoney.beanzip.domain.keycap.entity.*;
import com.ggukmoney.beanzip.domain.keycap.repository.*;
import com.ggukmoney.beanzip.domain.point.entity.PointAccount;
import com.ggukmoney.beanzip.domain.point.repository.PointAccountRepository;
import com.ggukmoney.beanzip.domain.tap.service.UserTapProgressService;
import com.ggukmoney.beanzip.domain.user.entity.AppUser;
import com.ggukmoney.beanzip.domain.user.repository.AppUserRepository;
import com.ggukmoney.beanzip.global.config.*;
import com.ggukmoney.beanzip.global.config.entity.AppConfig;
import com.ggukmoney.beanzip.global.config.repository.AppConfigRepository;
import com.ggukmoney.beanzip.support.FullStackIntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import com.ggukmoney.beanzip.domain.keycap.service.KeycapRewardSelector;
import java.util.List;
import java.util.concurrent.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.anyList;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import java.time.Instant;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real authenticated HTTP + PostgreSQL + Redis: old app, cutover, new draw, old retries. */
class KeycapEconomyCompatibilityIntegrationTest extends FullStackIntegrationTestSupport {
    @Autowired AppUserRepository users;
    @Autowired PointAccountRepository points;
    @Autowired KeycapBoxAccountRepository wallets;
    @Autowired KeycapRepository catalog;
    @Autowired UserKeycapRepository keycaps;
    @Autowired UserTapProgressService progress;
    @Autowired TapPolicyConfig tapPolicy;
    @Autowired AppConfigRepository configs;
    @Autowired KeycapBoxPolicyConfig economy;

    @MockitoBean KeycapRewardSelector selector;

    @BeforeEach void startWithLegacyPolicy() {
        jdbcTemplate.update("delete from app_config where config_key in (?,?,?)",
                KeycapBoxPolicyConfig.KEY_FREE_OPEN_LIMIT,KeycapBoxPolicyConfig.KEY_AD_OPEN_LIMIT,KeycapBoxPolicyConfig.KEY_SHARD_ENABLED);
        configs.save(AppConfig.createFor(KeycapBoxPolicyConfig.KEY_FREE_OPEN_LIMIT,"2",Instant.EPOCH));
        configs.save(AppConfig.createFor(KeycapBoxPolicyConfig.KEY_AD_OPEN_LIMIT,"6",Instant.EPOCH));
        economy.refresh();
        when(selector.select(anyList())).thenAnswer(i -> i.<List<Keycap>>getArgument(0).getFirst());
    }

    @Test void inFlightLegacyOpenAndNewDrawSerializeAcrossCutoverWithoutLosingProgress() throws Exception {
        var user=users.save(AppUser.createActive("compat-race-"+UUID.randomUUID(),null));
        points.save(PointAccount.createFor(user));
        var wallet=KeycapBoxAccount.createFor(user, Instant.now()); wallet.addBoxes(10); wallets.save(wallet);
        progress.createFor(user,tapPolicy);
        var key=catalog.findByAcquisitionTypeAndActiveTrueOrderBySortOrderAscCodeAsc(Keycap.AcquisitionType.BOX).getFirst();
        var collecting=UserKeycap.createInProgress(user,key); collecting.addShard(3,Instant.EPOCH); keycaps.save(collecting);
        var tokens=saveTokenBackedSession(user.getId(),UUID.randomUUID().toString());
        String auth="Bearer "+tokens.accessToken();
        var oldRequestLocked=new CountDownLatch(1);
        var releaseOldRequest=new CountDownLatch(1);
        var selections=new java.util.concurrent.atomic.AtomicInteger();
        doAnswer(i -> {
            if(selections.incrementAndGet()==1) {
                oldRequestLocked.countDown();
                if(!releaseOldRequest.await(20,TimeUnit.SECONDS)) throw new IllegalStateException("Old request release timeout");
            }
            return i.<List<Keycap>>getArgument(0).getFirst();
        }).when(selector).select(anyList());
        var executor=Executors.newFixedThreadPool(2);
        try {
            var oldOpen=executor.submit(() -> mockMvc.perform(post("/api/keycap-boxes/open").header("Authorization",auth)
                    .header("Idempotency-Key","race-old").contentType(MediaType.APPLICATION_JSON).content("{\"openMethod\":\"FREE\"}"))
                    .andExpect(status().isOk()).andReturn());
            assertThat(oldRequestLocked.await(10,TimeUnit.SECONDS)).isTrue();
            var cutover=Instant.now();
            configs.save(AppConfig.createFor(KeycapBoxPolicyConfig.KEY_FREE_OPEN_LIMIT,"0",cutover));
            configs.save(AppConfig.createFor(KeycapBoxPolicyConfig.KEY_AD_OPEN_LIMIT,"0",cutover));
        configs.save(AppConfig.createFor(KeycapBoxPolicyConfig.KEY_SHARD_ENABLED,"true",cutover));
            var newDraw=executor.submit(() -> mockMvc.perform(post("/api/keycaps/draw").header("Authorization",auth)
                    .header("Idempotency-Key","race-new")).andExpect(status().isOk()).andReturn());
            // Observe the DB lock, not a sleep-based expectation of which thread runs first.
            long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);
            int waiting=0;
            while(waiting==0 && System.nanoTime()<deadline) {
                waiting=jdbcTemplate.queryForObject("select count(*) from pg_locks l join pg_stat_activity a on a.pid=l.pid where not l.granted and a.query like '%app_user%'",Integer.class);
                if(waiting==0) Thread.sleep(25);
            }
            assertThat(waiting).isPositive();
            releaseOldRequest.countDown();
            var json=new tools.jackson.databind.ObjectMapper();
            var opened=json.readTree(oldOpen.get(20,TimeUnit.SECONDS).getResponse().getContentAsString()).get("data");
            var drawn=json.readTree(newDraw.get(20,TimeUnit.SECONDS).getResponse().getContentAsString()).get("data");
            int expected=10-1+3+opened.get("shardCount").asInt()-5;
            assertThat(drawn.get("shardBalance").asInt()).isEqualTo(expected);
            assertThat(wallets.findByUserId(user.getId()).orElseThrow().getShardBalance()).isEqualTo(expected);
            assertThat(pendingShardCount(user.getId())).isZero();
            assertThat(keycaps.findByUserIdWithKeycapOrderByKeycapSortOrderAscCodeAsc(user.getId()).getFirst().getLevel()).isEqualTo(1);
        } finally { releaseOldRequest.countDown(); executor.shutdownNow(); }
    }

    @Test void publishedClientsPreserveProgressAndReplayAcrossOneWayCutover() throws Exception {
        AppUser user=users.save(AppUser.createActive("compat-"+UUID.randomUUID(),null));
        points.save(PointAccount.createFor(user));
        KeycapBoxAccount wallet=KeycapBoxAccount.createFor(user, Instant.now()); wallet.addBoxes(10); wallets.save(wallet);
        progress.createFor(user,tapPolicy);
        var key=catalog.findByAcquisitionTypeAndActiveTrueOrderBySortOrderAscCodeAsc(Keycap.AcquisitionType.BOX).getFirst();
        var collecting=UserKeycap.createInProgress(user,key); collecting.addShard(3,Instant.EPOCH); keycaps.save(collecting);
        var tokens=saveTokenBackedSession(user.getId(),UUID.randomUUID().toString());
        String auth="Bearer "+tokens.accessToken();

        mockMvc.perform(get("/api/keycap-boxes/status").header("Authorization",auth))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.boxBalance").value(10))
                .andExpect(jsonPath("$.data.canFreeOpen").value(true)).andExpect(jsonPath("$.data.canDraw").value(false));
        mockMvc.perform(get("/api/keycaps/me").header("Authorization",auth))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.keycaps[0].shardCount").value(3));
        mockMvc.perform(post("/api/keycaps/draw").header("Authorization",auth).header("Idempotency-Key","early"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("KEYCAP_DRAW_NOT_ACTIVE"));
        mockMvc.perform(post("/api/keycap-boxes/bulk-open").header("Authorization",auth).header("Idempotency-Key","bulk"))
                .andExpect(status().isForbidden());

        UUID session=UUID.randomUUID();
        String batch="{\"tapSessionId\":\""+session+"\",\"sequence\":1,\"submittedCount\":25}";
        mockMvc.perform(post("/api/tap/batches").header("Authorization",auth).contentType(MediaType.APPLICATION_JSON).content(batch))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.boxesDropped").value(1))
                .andExpect(jsonPath("$.data.boxBalance").value(11)).andExpect(jsonPath("$.data.canFreeOpen").value(true));
        assertThat(pendingShardCount(user.getId())).isEqualTo(3);
        mockMvc.perform(post("/api/tap/batches").header("Authorization",auth).contentType(MediaType.APPLICATION_JSON).content(batch))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.pointsAwarded").value(0))
                .andExpect(jsonPath("$.data.boxBalance").value(11));

        String free="{\"openMethod\":\"FREE\"}";
        var opened=mockMvc.perform(post("/api/keycap-boxes/open").header("Authorization",auth).header("Idempotency-Key","old-open")
                        .contentType(MediaType.APPLICATION_JSON).content(free))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.shardCount").isNumber()).andReturn();
        assertThat(wallets.findByUserId(user.getId()).orElseThrow().getBoxBalance()).isEqualTo(10);
        long pending=pendingShardCount(user.getId());
        assertThat(pending).isBetween(4L,5L);
        int available=Math.toIntExact(10+pending);

        Instant cutover=Instant.now();
        configs.save(AppConfig.createFor(KeycapBoxPolicyConfig.KEY_FREE_OPEN_LIMIT,"0",cutover));
        configs.save(AppConfig.createFor(KeycapBoxPolicyConfig.KEY_AD_OPEN_LIMIT,"0",cutover));
        configs.save(AppConfig.createFor(KeycapBoxPolicyConfig.KEY_SHARD_ENABLED,"true",cutover));
        mockMvc.perform(get("/api/keycap-boxes/status").header("Authorization",auth))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.shardBalance").value(available))
                .andExpect(jsonPath("$.data.boxBalance").value(0));
        assertThat(pendingShardCount(user.getId())).isEqualTo(pending);
        mockMvc.perform(post("/api/keycap-boxes/open").header("Authorization",auth).header("Idempotency-Key","new-old-open")
                        .contentType(MediaType.APPLICATION_JSON).content(free))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("KEYCAP_BOX_OPEN_RETIRED"));
        var replay=mockMvc.perform(post("/api/keycap-boxes/open").header("Authorization",auth).header("Idempotency-Key","old-open")
                        .contentType(MediaType.APPLICATION_JSON).content(free))
                .andExpect(status().isOk()).andReturn();
        assertThat(replay.getResponse().getContentAsString()).isEqualTo(opened.getResponse().getContentAsString());

        var drawn=mockMvc.perform(post("/api/keycaps/draw").header("Authorization",auth).header("Idempotency-Key","draw"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.shardsSpent").value(5))
                .andExpect(jsonPath("$.data.shardBalance").value(available-5)).andExpect(jsonPath("$.data.level").value(1)).andReturn();
        assertThat(pendingShardCount(user.getId())).isZero();
        var drawReplay=mockMvc.perform(post("/api/keycaps/draw").header("Authorization",auth).header("Idempotency-Key","draw"))
                .andExpect(status().isOk()).andReturn();
        assertThat(drawReplay.getResponse().getContentAsString()).isEqualTo(drawn.getResponse().getContentAsString());
        assertThat(wallets.findByUserId(user.getId()).orElseThrow().getShardBalance()).isEqualTo(available-5);

        Instant reopen=Instant.now();
        configs.save(AppConfig.createFor(KeycapBoxPolicyConfig.KEY_FREE_OPEN_LIMIT,"2",reopen));
        configs.save(AppConfig.createFor(KeycapBoxPolicyConfig.KEY_AD_OPEN_LIMIT,"6",reopen));
        var restarted=new KeycapBoxPolicyConfig(new AppConfigBatchLoader(configs));
        assertThat(restarted.isShardEconomyEnabled()).isTrue();
        mockMvc.perform(post("/api/keycap-boxes/open").header("Authorization",auth).header("Idempotency-Key","after-reopen")
                        .contentType(MediaType.APPLICATION_JSON).content(free))
                .andExpect(status().isConflict());
        assertThat(wallets.findByUserId(user.getId()).orElseThrow().getShardBalance()).isEqualTo(available-5);
    }
    @Test void progressFundsDrawBeforeAffordabilityAndSelectedProgressBecomesNewLevelOne() throws Exception {
        var user=users.save(AppUser.createActive("progress-funds-"+UUID.randomUUID(),null));
        points.save(PointAccount.createFor(user)); progress.createFor(user,tapPolicy);
        var wallet=KeycapBoxAccount.createFor(user, Instant.now()); wallet.addBoxes(2); wallets.save(wallet);
        var key=catalog.findByAcquisitionTypeAndActiveTrueOrderBySortOrderAscCodeAsc(Keycap.AcquisitionType.BOX).getFirst();
        var pending=UserKeycap.createInProgress(user,key); pending.addShard(3,Instant.EPOCH); keycaps.save(pending);
        configs.save(AppConfig.createFor(KeycapBoxPolicyConfig.KEY_SHARD_ENABLED,"true",Instant.now()));
        doReturn(key).when(selector).select(anyList());
        var tokens=saveTokenBackedSession(user.getId(),UUID.randomUUID().toString()); String auth="Bearer "+tokens.accessToken();
        mockMvc.perform(get("/api/keycap-boxes/status").header("Authorization",auth))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.canDraw").value(true))
                .andExpect(jsonPath("$.data.shardBalance").value(5));
        mockMvc.perform(post("/api/keycaps/draw").header("Authorization",auth).header("Idempotency-Key","funded"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.level").value(1))
                .andExpect(jsonPath("$.data.shardBalance").value(0));
        assertThat(pendingShardCount(user.getId())).isZero();
        assertThat(wallets.findByUserId(user.getId()).orElseThrow().getShardBalance()).isZero();
    }
    @Test void zeroOpenLimitsKeepLegacyEconomyAndNeverConsumePendingProgress() throws Exception {
        var user=users.save(AppUser.createActive("zero-limits-"+UUID.randomUUID(),null));
        points.save(PointAccount.createFor(user)); progress.createFor(user,tapPolicy);
        var wallet=KeycapBoxAccount.createFor(user, Instant.now()); wallet.addBoxes(6); wallets.save(wallet);
        var at=Instant.now();
        configs.save(AppConfig.createFor(KeycapBoxPolicyConfig.KEY_FREE_OPEN_LIMIT,"0",at));
        configs.save(AppConfig.createFor(KeycapBoxPolicyConfig.KEY_AD_OPEN_LIMIT,"0",at));
        var tokens=saveTokenBackedSession(user.getId(),UUID.randomUUID().toString());
        mockMvc.perform(get("/api/keycap-boxes/status").header("Authorization","Bearer "+tokens.accessToken()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.canDraw").value(false))
                .andExpect(jsonPath("$.data.boxBalance").value(6));
        assertThat(new KeycapBoxPolicyConfig(new AppConfigBatchLoader(configs)).isShardEconomyEnabled()).isFalse();
    }
    @org.springframework.beans.factory.annotation.Autowired com.ggukmoney.beanzip.global.config.ops.OpsConfigService ops;
    @Test void simultaneousEconomicActivationSavesOnlyOneTrueHistory() throws Exception {
        configs.save(AppConfig.createFor(KeycapBoxPolicyConfig.KEY_SHARD_ENABLED,"false",Instant.EPOCH));
        String version=ops.current(KeycapBoxPolicyConfig.KEY_SHARD_ENABLED).getPublicId().toString();
        var ready=new CountDownLatch(2);var start=new CountDownLatch(1);
        Callable<String> activation=()->{ready.countDown();start.await();try{
            ops.change(KeycapBoxPolicyConfig.KEY_SHARD_ENABLED,version,"true","test","activate");return "saved";
        }catch(IllegalArgumentException rejected){return "rejected";}};
        try(var executor=Executors.newFixedThreadPool(2)) {
            var one=executor.submit(activation);var two=executor.submit(activation);
            assertThat(ready.await(5,TimeUnit.SECONDS)).isTrue();start.countDown();
            assertThat(List.of(one.get(30,TimeUnit.SECONDS),two.get(30,TimeUnit.SECONDS))).containsExactlyInAnyOrder("saved","rejected");
        }
        assertThat(jdbcTemplate.queryForObject("select count(*) from app_config where config_key=? and config_value='true'::jsonb",Long.class,
                KeycapBoxPolicyConfig.KEY_SHARD_ENABLED)).isEqualTo(1);
    }
    private long pendingShardCount(UUID userId) {
        return jdbcTemplate.queryForObject(
                "select coalesce(sum(shard_count), 0) from user_keycap where user_id = ? and status = 'IN_PROGRESS'",
                Long.class, userId);
    }

}
