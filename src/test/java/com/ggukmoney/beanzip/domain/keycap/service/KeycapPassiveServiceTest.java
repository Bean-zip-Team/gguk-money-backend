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
        when(config.snapshot()).thenReturn(old);
        var rewards=mock(TapRewardService.class);
        var points=mock(PointAccountService.class); var wallets=mock(KeycapBoxAccountService.class);
        var account=PointAccount.createFor(user); var wallet=KeycapBoxAccount.createFor(user);
        when(points.getForUser(any())).thenReturn(account); when(wallets.getForUser(any())).thenReturn(wallet);
        when(rewards.award(any(),any(),any(),anyInt(),anyBoolean(),any(),any())).thenAnswer(i ->
                new TapRewardService.Award(i.getArgument(3),0,0,null,null,null,account,wallet));
        var service=new KeycapPassiveService(mock(UserRewardLock.class),equipment,checkpoints,
                mock(KeycapPassiveSettlementRepository.class),config,rewards,new ObjectMapper(),Clock.systemUTC(),points,wallets);
        var start=Instant.parse("2026-10-01T00:00:00Z");
        service.settleLocked(user,start,"init",new KeycapPassiveRoller());
        when(config.snapshot()).thenReturn(next);
        var result=service.settleLocked(user,start.plus(Duration.ofDays(30)),"transition",new KeycapPassiveRoller());
        assertThat(result.autoClicksGranted()).isEqualTo(100000);
        assertThat(result.capped()).isTrue();
        assertThat(stored.get().getClicksPerDay()).isEqualTo(3333);
        assertThat(stored.get().getCapDays()).isEqualTo(30);
    }
}
