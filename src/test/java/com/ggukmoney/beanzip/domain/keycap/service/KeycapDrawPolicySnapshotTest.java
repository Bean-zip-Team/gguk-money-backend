package com.ggukmoney.beanzip.domain.keycap.service;

import com.ggukmoney.beanzip.domain.keycap.entity.*;
import com.ggukmoney.beanzip.domain.keycap.repository.*;
import com.ggukmoney.beanzip.domain.point.service.*;
import com.ggukmoney.beanzip.domain.promotion.service.*;
import com.ggukmoney.beanzip.domain.user.entity.AppUser;
import com.ggukmoney.beanzip.domain.user.service.*;
import com.ggukmoney.beanzip.global.config.*;
import com.ggukmoney.beanzip.global.config.entity.AppConfig;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class KeycapDrawPolicySnapshotTest {
    @Test void aDelayedDisplayRefreshCannotChangeAnAlreadyReadDrawPrice() {
        var loader=mock(AppConfigBatchLoader.class);
        var policy=spy(new KeycapBoxPolicyConfig(loader));
        var current=List.of(AppConfig.createFor(KeycapBoxPolicyConfig.KEY_SHARD_ENABLED,"true",Instant.EPOCH),
                AppConfig.createFor(KeycapBoxPolicyConfig.KEY_DRAW_PRICE,"10",Instant.EPOCH));
        var old=List.of(AppConfig.createFor(KeycapBoxPolicyConfig.KEY_SHARD_ENABLED,"true",Instant.EPOCH),
                AppConfig.createFor(KeycapBoxPolicyConfig.KEY_DRAW_PRICE,"5",Instant.EPOCH));
        when(loader.loadWithHistory(any(),anyString(),any())).thenReturn(current);
        // Reproduce an older display read publishing after the fresh phase query, before the price getter.
        doAnswer(invocation -> {
            boolean phase=(boolean)invocation.callRealMethod();
            when(loader.loadWithHistory(any(),anyString(),any())).thenReturn(old);
            policy.refresh();
            return phase;
        }).when(policy).isShardEconomyEnabled();
        var user=mock(AppUser.class); var userId=UUID.randomUUID(); when(user.getId()).thenReturn(userId);
        var lock=mock(UserRewardLock.class); when(lock.acquire(userId)).thenReturn(user);
        var wallets=mock(KeycapBoxAccountService.class); var wallet=KeycapBoxAccount.createFor(user, Instant.parse("2026-10-14T00:00:00Z")); wallet.addShards(20);
        when(wallets.getForUserForUpdate(userId)).thenReturn(wallet);
        var draws=mock(KeycapDrawRepository.class); when(draws.save(any())).thenAnswer(i->i.getArgument(0));
        var catalog=mock(KeycapRepository.class); var selected=Keycap.createFor("PRICE_RACE","Price",Keycap.Grade.COMMON,10,1,null,null,1);
        when(catalog.findByAcquisitionTypeAndActiveTrueOrderBySortOrderAscCodeAsc(any())).thenReturn(List.of(selected));
        var selector=mock(KeycapRewardSelector.class); when(selector.select(any())).thenReturn(selected);
        var owned=mock(UserKeycapRepository.class); when(owned.save(any())).thenAnswer(i->i.getArgument(0));
        var service=new KeycapDrawService(wallets,draws,catalog,owned,mock(UserService.class),selector,policy,
                mock(PointAccountService.class),mock(PointLedgerService.class),mock(PromotionGrantIssuer.class),
                mock(KeycapFiveCompletionTrigger.class),mock(PlatformTransactionManager.class),
                Clock.fixed(Instant.parse("2026-10-14T00:00:00Z"),ZoneOffset.UTC),lock);
        var response=service.draw(userId,"price-race");
        assertThat(response.shardsSpent()).isEqualTo(10);
        assertThat(wallet.getShardBalance()).isEqualTo(10);
    }
}
