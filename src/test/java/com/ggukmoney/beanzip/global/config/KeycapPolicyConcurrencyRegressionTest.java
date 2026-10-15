package com.ggukmoney.beanzip.global.config;
import com.ggukmoney.beanzip.global.config.entity.AppConfig;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
class KeycapPolicyConcurrencyRegressionTest {
    private List<AppConfig> flag(boolean enabled){return List.of(AppConfig.createFor(KeycapPassivePolicyConfig.KEY_ENABLED,Boolean.toString(enabled),Instant.EPOCH));}
    @Test void settlementSnapshotNeverPublishesIntoDisplayCache(){
        var loader=mock(AppConfigBatchLoader.class); var config=new KeycapPassivePolicyConfig(loader);
        when(loader.loadWithHistory(any(),any(),any())).thenReturn(flag(false)); config.refresh();
        when(loader.loadWithHistory(any(),any(),any())).thenReturn(flag(true));
        assertThat(config.settlementSnapshot(Instant.now()).enabled()).isTrue();
        assertThat(config.snapshot().enabled()).isFalse();
    }
    @Test void slowSettlementDoesNotSerializeOtherUsersPolicyReads() throws Exception {
        var loader=mock(AppConfigBatchLoader.class); var config=new KeycapPassivePolicyConfig(loader);
        var entered=new CountDownLatch(1); var release=new CountDownLatch(1); var calls=new AtomicInteger();
        when(loader.loadWithHistory(any(),any(),any())).thenAnswer(i->{if(calls.incrementAndGet()==1){entered.countDown();assertThat(release.await(5,TimeUnit.SECONDS)).isTrue();}return flag(true);});
        var workers=Executors.newFixedThreadPool(2);
        try {
            var first=workers.submit(()->config.settlementSnapshot(Instant.now()));
            assertThat(entered.await(3,TimeUnit.SECONDS)).isTrue();
            var second=workers.submit(()->config.settlementSnapshot(Instant.now()));
            assertThat(second.get(1,TimeUnit.SECONDS).loaded()).isTrue();
            release.countDown(); assertThat(first.get(3,TimeUnit.SECONDS).loaded()).isTrue();
        } finally {release.countDown();workers.shutdownNow();}
    }
    @Test void committedRefreshDuringOldReadIsCoalescedThenReloaded() throws Exception {
        var loader=mock(AppConfigBatchLoader.class); var config=new KeycapPassivePolicyConfig(loader);
        var entered=new CountDownLatch(1); var release=new CountDownLatch(1); var calls=new AtomicInteger();
        when(loader.loadWithHistory(any(),any(),any())).thenAnswer(i->{if(calls.incrementAndGet()==1){entered.countDown();assertThat(release.await(5,TimeUnit.SECONDS)).isTrue();return flag(false);}return flag(true);});
        var workers=Executors.newFixedThreadPool(2);
        try {
            var first=workers.submit(config::refresh); assertThat(entered.await(3,TimeUnit.SECONDS)).isTrue();
            var afterCommit=workers.submit(config::refresh); afterCommit.get(1,TimeUnit.SECONDS);
            release.countDown(); first.get(3,TimeUnit.SECONDS);
            assertThat(config.snapshot().enabled()).isTrue(); assertThat(calls.get()).isEqualTo(2);
        } finally {release.countDown();workers.shutdownNow();}
    }
}
