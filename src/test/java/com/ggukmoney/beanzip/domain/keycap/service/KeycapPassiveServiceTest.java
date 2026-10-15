package com.ggukmoney.beanzip.domain.keycap.service;

import com.ggukmoney.beanzip.domain.keycap.entity.*;
import com.ggukmoney.beanzip.domain.keycap.repository.*;
import com.ggukmoney.beanzip.domain.keycap.passive.*;
import com.ggukmoney.beanzip.domain.point.entity.PointAccount;
import com.ggukmoney.beanzip.domain.point.service.PointAccountService;
import com.ggukmoney.beanzip.domain.tap.service.TapRewardService;
import com.ggukmoney.beanzip.domain.user.entity.AppUser;
import com.ggukmoney.beanzip.domain.user.service.UserRewardLock;
import com.ggukmoney.beanzip.global.config.KeycapPassivePolicyConfig;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class KeycapPassiveServiceTest {
    private static com.ggukmoney.beanzip.global.config.KeycapBoxPolicyConfig shardEconomy() {
        var value=mock(com.ggukmoney.beanzip.global.config.KeycapBoxPolicyConfig.class);
        when(value.isShardEconomyEnabled()).thenReturn(true); return value;
    }
    @Test void legacyEconomyIgnoresPassiveHistoryWithoutWritingCheckpoint() {
        var config=mock(KeycapPassivePolicyConfig.class);
        var snapshot=KeycapPassivePolicyConfig.decode(Map.of("keycap.passive.enabled","true"));
        when(config.settlementSnapshot(any())).thenReturn(snapshot);
        when(config.snapshot()).thenReturn(snapshot);
        var checkpoints=mock(KeycapPassiveCheckpointRepository.class);
        when(checkpoints.save(any())).thenAnswer(i -> i.getArgument(0));
        var points=mock(PointAccountService.class);
        var wallets=mock(KeycapBoxAccountService.class);
        var user=mock(AppUser.class); when(user.getId()).thenReturn(UUID.randomUUID());
        when(points.getForUser(any())).thenReturn(PointAccount.createFor(user));
        when(wallets.getForUserForUpdate(any())).thenReturn(KeycapBoxAccount.createFor(user, Instant.EPOCH));
        var service=new KeycapPassiveService(mock(UserRewardLock.class),mock(UserKeycapRepository.class),checkpoints,
                mock(KeycapPassiveSettlementRepository.class),config,mock(TapRewardService.class),new ObjectMapper(),
                Clock.systemUTC(),points,wallets,mock(com.ggukmoney.beanzip.global.config.KeycapBoxPolicyConfig.class));
        assertThat(service.status(user.getId()).enabled()).isFalse();
        assertThat(service.settleLocked(user,Instant.now(),"legacy",new KeycapPassiveRoller()).autoClicksGranted()).isZero();
        verifyNoInteractions(checkpoints,wallets);
        verify(config,never()).settlementSnapshot(any());
    }

    @Test void unloadedPolicyCannotWriteOrAdvanceAnyCheckpoint() {
        var config=mock(KeycapPassivePolicyConfig.class);
        var unknown=new KeycapPassivePolicyConfig.Snapshot(false,KeycapPassivePolicy.defaults(),Instant.EPOCH,7,List.of(),false);
        when(config.settlementSnapshot(any())).thenReturn(unknown);
        when(config.snapshot()).thenReturn(unknown);
        var equipment=mock(UserKeycapRepository.class);
        var checkpoints=mock(KeycapPassiveCheckpointRepository.class);
        var rewards=mock(TapRewardService.class);
        var points=mock(PointAccountService.class);
        var wallets=mock(KeycapBoxAccountService.class);
        var service=new KeycapPassiveService(mock(UserRewardLock.class),equipment,checkpoints,
                mock(KeycapPassiveSettlementRepository.class),config,rewards,new ObjectMapper(),Clock.systemUTC(),points,wallets,shardEconomy());
        var user=mock(AppUser.class);
        assertThatThrownBy(() -> service.settleLocked(user,Instant.now(),"unloaded",new KeycapPassiveRoller()))
                .hasMessageContaining("503").hasMessageContaining("PASSIVE_POLICY_UNAVAILABLE");
        assertThatThrownBy(() -> service.status(UUID.randomUUID())).hasMessageContaining("PASSIVE_POLICY_UNAVAILABLE");
        assertThatThrownBy(() -> service.refreshEquipmentLocked(UUID.randomUUID())).hasMessageContaining("PASSIVE_POLICY_UNAVAILABLE");
        verifyNoInteractions(checkpoints,equipment,rewards,points,wallets);
    }
    @Test void changedCapDaysClosesOldCheckpointWithItsPreviouslyValidatedAccrualCap() {
        var user=mock(AppUser.class);
        when(user.getId()).thenReturn(UUID.randomUUID());
        var gear=mock(UserKeycap.class); var keycap=mock(Keycap.class);
        when(gear.getKeycap()).thenReturn(keycap); when(keycap.getCode()).thenReturn("cheer"); when(gear.getLevel()).thenReturn(1);
        var equipment=mock(UserKeycapRepository.class);
        when(equipment.findByUserIdAndEquippedTrue(any())).thenReturn(Optional.of(gear));
        var checkpoints=mock(KeycapPassiveCheckpointRepository.class);
        var stored=new AtomicReference<KeycapPassiveCheckpoint>();
        when(checkpoints.findById(any())).thenAnswer(i -> Optional.ofNullable(stored.get()));
        when(checkpoints.save(any())).thenAnswer(i -> {stored.set(i.getArgument(0)); return stored.get();});
        var config=mock(KeycapPassivePolicyConfig.class);
        var old=KeycapPassivePolicyConfig.decode(Map.of("keycap.passive.enabled","true","keycap.passive.capDays","1",
                "keycap.passive.COMMON.autoClickBase","100000","keycap.passive.COMMON.autoClickPerLevel","0","keycap.passive.COMMON.autoClickCap","100000"));
        var next=KeycapPassivePolicyConfig.decode(Map.of("keycap.passive.enabled","true","keycap.passive.capDays","30",
                "keycap.passive.COMMON.autoClickBase","3333","keycap.passive.COMMON.autoClickPerLevel","0","keycap.passive.COMMON.autoClickCap","3333"));
        when(config.settlementSnapshot(any())).thenReturn(old);
        var rewards=mock(TapRewardService.class);
        var points=mock(PointAccountService.class); var wallets=mock(KeycapBoxAccountService.class);
        var account=PointAccount.createFor(user); var wallet=KeycapBoxAccount.createFor(user, Instant.EPOCH);
        when(points.getForUser(any())).thenReturn(account); when(wallets.getForUserForUpdate(any())).thenReturn(wallet);
        when(wallets.getAvailableShardBalance(any())).thenAnswer(i->wallet.getShardBalance());
        when(rewards.award(any(),any(),any(),anyInt(),anyBoolean(),any(),any())).thenAnswer(i ->
                new TapRewardService.Award(i.getArgument(3),0,0,null,null,null,account,wallet));
        var service=new KeycapPassiveService(mock(UserRewardLock.class),equipment,checkpoints,
                mock(KeycapPassiveSettlementRepository.class),config,rewards,new ObjectMapper(),Clock.systemUTC(),points,wallets,shardEconomy());
        var start=Instant.parse("2026-10-01T00:00:00Z");
        service.settleLocked(user,start,"init",new KeycapPassiveRoller());
        when(config.settlementSnapshot(any())).thenReturn(next);
        var result=service.settleLocked(user,start.plus(Duration.ofDays(30)),"transition",new KeycapPassiveRoller());
        assertThat(result.autoClicksGranted()).isEqualTo(100000);
        assertThat(result.capped()).isTrue();
        assertThat(stored.get().getClicksPerDay()).isEqualTo(3333);
        assertThat(stored.get().getCapDays()).isEqualTo(30);
    }
    @Test void settlementWithoutAutoClicksIncludesLegacyShardsInWalletBalance() {
        var user=mock(AppUser.class); when(user.getId()).thenReturn(UUID.randomUUID());
        var config=mock(KeycapPassivePolicyConfig.class);
        var snapshot=KeycapPassivePolicyConfig.decode(Map.of("keycap.passive.enabled","false"));
        when(config.settlementSnapshot(any())).thenReturn(snapshot);
        var checkpoints=mock(KeycapPassiveCheckpointRepository.class);
        when(checkpoints.save(any())).thenAnswer(i -> i.getArgument(0));
        var wallets=mock(KeycapBoxAccountService.class);
        var wallet=KeycapBoxAccount.createFor(user, Instant.EPOCH); wallet.addShards(9);
        when(wallets.getForUser(any())).thenReturn(wallet);
        when(wallets.getAvailableShardBalance(any())).thenReturn(12);
        var points=mock(PointAccountService.class); when(points.getForUser(any())).thenReturn(PointAccount.createFor(user));
        var service=new KeycapPassiveService(mock(UserRewardLock.class),mock(UserKeycapRepository.class),checkpoints,
                mock(KeycapPassiveSettlementRepository.class),config,mock(TapRewardService.class),new ObjectMapper(),
                Clock.systemUTC(),points,wallets,shardEconomy());
        var result=service.settleLocked(user,Instant.parse("2026-10-13T00:00:00Z"),"legacy-init",new KeycapPassiveRoller());
        assertThat(result.autoClicksGranted()).isZero();
        assertThat(result.shardBalance()).isEqualTo(12);
    }
    @Test void currentDecisionCreatesFirstCheckpointWhileOffThenAccruesFromActivation() {
        // No explicit specification defines initial checkpoint timing: preserve the current decision.
        var user=mock(AppUser.class); when(user.getId()).thenReturn(UUID.randomUUID());
        var gear=mock(UserKeycap.class); var keycap=mock(Keycap.class);
        when(gear.getKeycap()).thenReturn(keycap); when(keycap.getCode()).thenReturn("cheer"); when(gear.getLevel()).thenReturn(1);
        var equipment=mock(UserKeycapRepository.class); when(equipment.findByUserIdAndEquippedTrue(any())).thenReturn(Optional.of(gear));
        var checkpoints=mock(KeycapPassiveCheckpointRepository.class); var stored=new AtomicReference<KeycapPassiveCheckpoint>();
        when(checkpoints.findById(any())).thenAnswer(i->Optional.ofNullable(stored.get()));
        when(checkpoints.save(any())).thenAnswer(i->{stored.set(i.getArgument(0));return stored.get();});
        var start=Instant.parse("2026-10-01T00:00:00Z"); var activation=start.plus(Duration.ofHours(12));
        var config=mock(KeycapPassivePolicyConfig.class);
        var off=KeycapPassivePolicyConfig.decode(Map.of());
        var on=new KeycapPassivePolicyConfig.Snapshot(true,KeycapPassivePolicy.defaults(),activation,7);
        when(config.settlementSnapshot(any())).thenReturn(off,on);
        var points=mock(PointAccountService.class); var account=PointAccount.createFor(user); when(points.getForUser(any())).thenReturn(account);
        var wallets=mock(KeycapBoxAccountService.class); var wallet=KeycapBoxAccount.createFor(user, Instant.EPOCH);
        var rewards=mock(TapRewardService.class); when(rewards.award(any(),any(),any(),anyInt(),anyBoolean(),any(),any())).thenAnswer(i->
                new TapRewardService.Award(i.getArgument(3),0,0,null,null,null,account,wallet));
        var service=new KeycapPassiveService(mock(UserRewardLock.class),equipment,checkpoints,mock(KeycapPassiveSettlementRepository.class),
                config,rewards,new ObjectMapper(),Clock.systemUTC(),points,wallets,shardEconomy());
        assertThat(service.settleLocked(user,start,"off-first",new KeycapPassiveRoller()).autoClicksGranted()).isZero();
        assertThat(stored.get().getLastActivityAt()).isEqualTo(start);
        assertThat(service.settleLocked(user,start.plus(Duration.ofDays(1)),"active",new KeycapPassiveRoller()).autoClicksGranted()).isEqualTo(30);
    }
}
