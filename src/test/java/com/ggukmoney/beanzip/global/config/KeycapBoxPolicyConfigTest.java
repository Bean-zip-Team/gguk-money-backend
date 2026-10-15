package com.ggukmoney.beanzip.global.config;
import com.ggukmoney.beanzip.global.config.entity.AppConfig;
import com.ggukmoney.beanzip.global.config.repository.AppConfigRepository;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
class KeycapBoxPolicyConfigTest {
    private final AppConfigRepository repository=mock(AppConfigRepository.class);
    private final KeycapBoxPolicyConfig config=new KeycapBoxPolicyConfig(new AppConfigBatchLoader(repository));
    private static final String SWITCH="keycap.economy.shard.enabled";
    private AppConfig row(String key,String value,Instant at){return AppConfig.createFor(key,value,at);}
    private void rows(AppConfig... rows){when(repository.findLatestEffectiveWithHistory(any(),eq(SWITCH),any())).thenReturn(List.of(rows));}
    @Test void missingSwitchDefaultsOff(){assertThat(config.isShardEconomyEnabled()).isFalse();assertThat(config.drawPrice()).isEqualTo(5);}
    @Test void zeroOpenLimitsDoNotActivateShardEconomy(){
        var rows=List.of(row(KeycapBoxPolicyConfig.KEY_FREE_OPEN_LIMIT,"0",Instant.EPOCH),row(KeycapBoxPolicyConfig.KEY_AD_OPEN_LIMIT,"0",Instant.EPOCH));
        when(repository.findLatestEffectiveWithHistory(any(),eq(SWITCH),any())).thenReturn(rows);
        assertThat(config.isShardEconomyEnabled()).isFalse();
    }
    @Test void explicitSwitchActivatesWithoutZeroOpenLimits(){rows(row(SWITCH,"true",Instant.EPOCH));assertThat(config.isShardEconomyEnabled()).isTrue();}
    @Test void laterFalseCannotReverseActivationEvenAfterRestart(){
        rows(row(SWITCH,"true",Instant.EPOCH),row(SWITCH,"false",Instant.EPOCH.plusSeconds(10)));
        assertThat(new KeycapBoxPolicyConfig(new AppConfigBatchLoader(repository)).isShardEconomyEnabled()).isTrue();
    }
    @Test void invalidActivationHistoryFailsClosed(){
        rows(row(SWITCH,"yes",Instant.EPOCH));
        assertThatThrownBy(config::isShardEconomyEnabled).isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
          .extracting(e->((org.springframework.web.server.ResponseStatusException)e).getStatusCode().value()).isEqualTo(503);
    }
    @Test void databaseFailureFailsClosed(){
        when(repository.findLatestEffectiveWithHistory(any(),eq(SWITCH),any())).thenThrow(new IllegalStateException("unavailable"));
        assertThatThrownBy(config::isShardEconomyEnabled).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
    }
    @Test void freshPhaseUsesExactlyOneHistoryQuery(){config.isShardEconomyEnabled();verify(repository).findLatestEffectiveWithHistory(any(),eq(SWITCH),any());}
    @Test void drawPriceRefreshKeepsLastGoodValue(){rows(row(KeycapBoxPolicyConfig.KEY_DRAW_PRICE,"3",Instant.EPOCH));config.refresh();assertThat(config.drawPrice()).isEqualTo(3);
        rows(row(KeycapBoxPolicyConfig.KEY_DRAW_PRICE,"0",Instant.EPOCH));config.refresh();assertThat(config.drawPrice()).isEqualTo(3);}
    @Test void futureActivationIsNotAppliedEarly(){rows(row(SWITCH,"true",Instant.now().plusSeconds(3600)));assertThat(config.isShardEconomyEnabled()).isFalse();}
    @Test void heldEconomySnapshotKeepsItsPriceAcrossASeparateDisplayRefresh(){
        rows(row(SWITCH,"true",Instant.EPOCH),row(KeycapBoxPolicyConfig.KEY_DRAW_PRICE,"10",Instant.EPOCH));
        var held=config.requestSnapshot();
        rows(row(SWITCH,"true",Instant.EPOCH),row(KeycapBoxPolicyConfig.KEY_DRAW_PRICE,"5",Instant.EPOCH));
        config.refresh();
        assertThat(held.shardEconomy()).isTrue();
        assertThat(held.drawPrice()).isEqualTo(10);
        assertThat(config.drawPrice()).isEqualTo(5);
    }
}
