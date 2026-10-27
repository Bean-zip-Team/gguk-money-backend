package com.ggukmoney.beanzip.global.config.ops;

import com.ggukmoney.beanzip.global.config.*;
import com.ggukmoney.beanzip.global.config.entity.AppConfig;
import com.ggukmoney.beanzip.global.config.repository.AppConfigRepository;
import com.ggukmoney.beanzip.domain.keycap.repository.KeycapRepository;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class PassiveEconomicPrerequisiteTest {
    @ParameterizedTest @ValueSource(booleans={false,true})
    void activationCannotBeSavedBeforeCutoverOrWhenEconomyLookupFails(boolean unavailable) {
        var repository=mock(AppConfigRepository.class);
        var passive=mock(KeycapPassivePolicyConfig.class);
        var current=AppConfig.createFor(KeycapPassivePolicyConfig.KEY_ENABLED,"false",Instant.EPOCH);
        var id=UUID.randomUUID(); ReflectionTestUtils.setField(current,"publicId",id);
        when(repository.findFirstByConfigKeyOrderByIdAsc(KeycapPassivePolicyConfig.KEY_ENABLED)).thenReturn(Optional.of(current));
        when(repository.findFirstByConfigKeyAndEffectiveAtLessThanEqualOrderByEffectiveAtDesc(eq(KeycapPassivePolicyConfig.KEY_ENABLED),any())).thenReturn(Optional.of(current));
        if(unavailable) when(repository.findLatestEffectiveWithHistory(any(),eq(KeycapBoxPolicyConfig.KEY_SHARD_ENABLED),any()))
                .thenThrow(new IllegalStateException("economic database unavailable"));
        else when(repository.findLatestEffectiveWithHistory(any(),eq(KeycapBoxPolicyConfig.KEY_SHARD_ENABLED),any()))
                .thenReturn(List.of(AppConfig.createFor(KeycapBoxPolicyConfig.KEY_SHARD_ENABLED,"false",Instant.EPOCH)));
        var mapper=new ObjectMapper();
        var service=new OpsConfigService(repository,new AppConfigValueValidator(mapper),
                new PolicyValueRules(mock(KeycapRepository.class),mapper),passive,
                new KeycapBoxPolicyConfig(new AppConfigBatchLoader(repository)));
        var operation=catchThrowable(()->service.change(KeycapPassivePolicyConfig.KEY_ENABLED,id.toString(),"true","test","activate"));
        if(unavailable) assertThat(operation).isInstanceOf(ResponseStatusException.class)
                .extracting(failure->((ResponseStatusException)failure).getStatusCode().value()).isEqualTo(503);
        else assertThat(operation).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("조각 경제로 전환한 후");
        verify(repository,never()).save(any(AppConfig.class));
        verify(repository,never()).findLatestEffectiveByConfigKeys(any(),any());
        verify(passive,never()).refresh();
    }
}
