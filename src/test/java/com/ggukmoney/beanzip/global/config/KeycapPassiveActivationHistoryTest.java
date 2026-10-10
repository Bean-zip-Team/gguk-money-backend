package com.ggukmoney.beanzip.global.config;

import com.ggukmoney.beanzip.global.config.entity.AppConfig;
import com.ggukmoney.beanzip.domain.keycap.passive.KeycapAutoClickAccrual.ActivePeriod;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class KeycapPassiveActivationHistoryTest {
    private final AppConfigBatchLoader loader=mock(AppConfigBatchLoader.class);
    private final KeycapPassivePolicyConfig config=new KeycapPassivePolicyConfig(loader);
    private final Instant start=Instant.parse("2026-10-01T00:00:00Z");

    @Test void repeatedEnableRowsAndNewEnabledAtDoNotEraseOlderIntervals() {
        when(loader.loadWithHistory(any(),anyString(),any())).thenReturn(List.of(
                row("true",0),row("true",1),row("false",2),row("true",4),row("false",5),
                AppConfig.createFor(KeycapPassivePolicyConfig.KEY_ENABLED_AT,"\"2026-10-05T00:00:00Z\"",start.plus(Duration.ofDays(4)))));
        config.refresh();
        assertThat(config.snapshot().loaded()).isTrue();
        assertThat(config.snapshot().enabled()).isFalse();
        assertThat(config.snapshot().activePeriods()).containsExactly(
                new ActivePeriod(start,start.plus(Duration.ofDays(2))),
                new ActivePeriod(start.plus(Duration.ofDays(4)),start.plus(Duration.ofDays(5))));
    }

    @Test void initialDatabaseFailureIsUnknownWhileSuccessfullyLoadedEmptyPolicyIsDisabled() {
        when(loader.loadWithHistory(any(),anyString(),any())).thenThrow(new IllegalStateException("DB unavailable"));
        config.refresh();
        assertThat(config.snapshot().loaded()).isFalse();
        doReturn(List.of()).when(loader).loadWithHistory(any(),anyString(),any());
        config.refresh();
        assertThat(config.snapshot().loaded()).isTrue();
        assertThat(config.snapshot().enabled()).isFalse();
        assertThat(config.snapshot().activePeriods()).isEmpty();
    }

    @Test void malformedHistoricalFlagRejectsEvenWhenTheLatestFlagIsValid() {
        when(loader.loadWithHistory(any(),anyString(),any())).thenReturn(List.of(row("true",0)));
        config.refresh();
        var good=config.snapshot();
        when(loader.loadWithHistory(any(),anyString(),any())).thenReturn(List.of(row("broken",0),row("false",1)));
        config.refresh();
        assertThat(config.snapshot()).isSameAs(good);
    }

    @Test void settlementReadsCommittedHistoryAndDoesNotUseStaleActivationOnDatabaseFailure() {
        when(loader.loadWithHistory(any(),anyString(),any())).thenReturn(List.of(row("true",0)));
        config.refresh();
        var good=config.snapshot();
        when(loader.loadWithHistory(any(),anyString(),any())).thenReturn(List.of(row("true",0),row("false",2)));
        assertThat(config.settlementSnapshot(start.plus(Duration.ofDays(3))).enabled()).isFalse();
        when(loader.loadWithHistory(any(),anyString(),any())).thenThrow(new IllegalStateException("DB unavailable"));
        assertThat(config.settlementSnapshot(start.plus(Duration.ofDays(4))).loaded()).isFalse();
        assertThat(config.snapshot().loaded()).isTrue();
        assertThat(good.enabled()).isTrue();
    }

    @Test void explicitStartRatioIsIntuitiveAndLegacyStrengthPairsRemainReadable() {
        String prefix="keycap.passive.COMMON.";
        assertThat(KeycapPassivePolicyConfig.DEFAULT_VALUES).containsKey(prefix+"startRatio");
        var legacy=KeycapPassivePolicyConfig.decode(Map.of(prefix+"startStrength","0.03",prefix+"maxStrength","0.1"));
        var next=KeycapPassivePolicyConfig.decode(Map.of(prefix+"startRatio","0.5",prefix+"maxStrength","0.1"));
        assertThat(legacy.policy().effects("main",1).shard().probability()).isCloseTo(0.03,within(1e-12));
        assertThat(next.policy().effects("main",1).shard().probability()).isCloseTo(0.05,within(1e-12));
        assertThat(next.policy().effects("main",50).shard().probability()).isEqualTo(0.1);
        assertThatThrownBy(() -> KeycapPassivePolicyConfig.decode(Map.of(prefix+"startRatio","1.01")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private AppConfig row(String value,int days) {
        return AppConfig.createFor(KeycapPassivePolicyConfig.KEY_ENABLED,value,start.plus(Duration.ofDays(days)));
    }

    @ParameterizedTest @ValueSource(booleans={false,true})
    void invalidPolicyCannotUseOlderActivationAndRecoversWithoutLosingClicks(boolean initiallyEnabled) {
        when(loader.loadWithHistory(any(),anyString(),any())).thenReturn(List.of(row(Boolean.toString(initiallyEnabled),0)));
        config.refresh();
        var good=config.snapshot();
        when(loader.loadWithHistory(any(),anyString(),any())).thenReturn(List.of(row(Boolean.toString(initiallyEnabled),0),row(Boolean.toString(!initiallyEnabled),2),
                AppConfig.createFor("keycap.passive.main.shard.probability","0.3",start.plus(Duration.ofDays(2)))));
        var pending=config.settlementSnapshot(start.plus(Duration.ofDays(3)));
        assertThat(pending.loaded()).isFalse();
        assertThat(config.snapshot()).isSameAs(good);
        doReturn(List.of(row(Boolean.toString(initiallyEnabled),0),row(Boolean.toString(!initiallyEnabled),2)))
                .when(loader).loadWithHistory(any(),anyString(),any());
        var recovered=config.settlementSnapshot(start.plus(Duration.ofDays(3)));
        assertThat(recovered.loaded()).isTrue();
        assertThat(recovered.enabled()).isEqualTo(!initiallyEnabled);
        var calculator=new com.ggukmoney.beanzip.domain.keycap.passive.KeycapAutoClickAccrual();
        var checkpoint=com.ggukmoney.beanzip.domain.keycap.passive.KeycapAutoClickAccrual.Checkpoint.initial(start,"cheer",1,60);
        var calculated=calculator.calculate(checkpoint,start.plus(Duration.ofDays(3)),
                new com.ggukmoney.beanzip.domain.keycap.passive.KeycapAutoClickAccrual.Policy(recovered.activePeriods(),7));
        assertThat(calculated.grantedClicks()).isEqualTo(initiallyEnabled?120:60);
    }
}

