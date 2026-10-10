package com.ggukmoney.beanzip.domain.keycap.service;

import com.ggukmoney.beanzip.domain.keycap.entity.*;
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
    @Autowired KeycapPassiveService passive;
    @Autowired TapBatchService taps;
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
    @Autowired AppConfigRepository configs;
    @Autowired KeycapPassivePolicyConfig policy;
    @Autowired TapPolicyConfig tapPolicy;
    @Autowired KeycapPassiveCheckpointRepository checkpoints;
    @Autowired PlatformTransactionManager tx;
    @MockitoBean KeycapRewardSelector selector;

    @BeforeEach void enable() {
        configs.save(AppConfig.createFor(KeycapPassivePolicyConfig.KEY_ENABLED,"true",Instant.now().minusSeconds(1)));
        configs.save(AppConfig.createFor(TapPolicyConfig.KEY_RATE_LIMIT_ENABLED,"false",Instant.now().minusSeconds(1)));
        policy.refresh(); tapPolicy.refresh();
    }

    private AppUser user(String code) {
        var user=users.save(AppUser.createActive("passive-"+UUID.randomUUID(),null));
        accounts.save(PointAccount.createFor(user));
        var wallet=KeycapBoxAccount.createFor(user); wallet.addShards(100); wallets.save(wallet);
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

    @Test void sameKeyAndSimultaneousRequestsPayExactlyOnceAndReplaySnapshot() throws Exception {
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
    }

    @Test void concurrentTapBatchesPersistOneOutcomeAndAutomaticClicksTakeAllowanceFirst() throws Exception {
        var user=user("cheer"); elapsed(user,1);
        var day=UserTapDaily.createFor(user,LocalDate.now(ZoneId.of("Asia/Seoul"))); day.addValidTaps(2990); dailies.save(day);
        var request=new TapBatchSubmitRequest(UUID.randomUUID(),1L,50);
        var results=concurrently(() -> taps.submitBatch(user.getId(),request),() -> taps.submitBatch(user.getId(),request));
        assertThat(results.get(0)).isEqualTo(results.get(1));
        var response=(TapBatchSubmitResponse)results.get(0);
        assertThat(response.autoClicksGranted()).isEqualTo(60);
        assertThat(response.acceptedCount()).isEqualTo(50);
        assertThat(response.effectiveCount()).isEqualTo(50);
        assertThat(response.effectiveTapCountToday()).isEqualTo(110);
        assertThat(progress.findByUserId(user.getId()).orElseThrow().getCumulativeMissionTapCount()).isZero();
        assertThat(dailies.findByUserIdAndTapDate(user.getId(),day.getTapDate()).orElseThrow().getValidTapCount()).isEqualTo(3000);
        assertThatThrownBy(() -> taps.submitBatch(user.getId(),new TapBatchSubmitRequest(request.tapSessionId(),1L,51)))
                .hasMessageContaining("TAP_BATCH_REQUEST_MISMATCH");
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
                .andExpect(jsonPath("$.data.pendingAutoClicks").value(0));
        mockMvc.perform(post("/api/keycaps/passive/settle").header("Authorization","Bearer "+token))
                .andExpect(status().isBadRequest());
        assertThat(keycaps.getKeycaps().keycaps()).allSatisfy(card -> assertThat(card.previewEffects().previewLevel()).isEqualTo(1));
        configs.save(AppConfig.createFor(KeycapPassivePolicyConfig.KEY_ENABLED,"false",Instant.now()));
        policy.refresh();
        assertThat(passive.settle(user.getId(),"disabled").autoClicksGranted()).isZero();
        assertThat(passive.status(user.getId()).enabled()).isFalse();
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
}
