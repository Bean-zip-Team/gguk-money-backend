package com.ggukmoney.beanzip.global.config.ops;
import com.ggukmoney.beanzip.global.config.*;
import com.ggukmoney.beanzip.global.config.entity.AppConfig;
import com.ggukmoney.beanzip.global.config.repository.AppConfigRepository;
import com.ggukmoney.beanzip.domain.keycap.repository.KeycapRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
class OpsConfigServiceTest {
    @Test void passiveSnapshotIsRefreshedOnlyAfterSuccessfulCommit() {
        var repository=mock(AppConfigRepository.class);
        var mapper=new ObjectMapper();
        var config=mock(KeycapPassivePolicyConfig.class);
        var current=AppConfig.createFor(KeycapPassivePolicyConfig.KEY_ENABLED,"false",Instant.EPOCH);
        var id=UUID.randomUUID();
        ReflectionTestUtils.setField(current,"publicId",id);
        when(repository.findFirstByConfigKeyOrderByIdAsc(anyString())).thenReturn(Optional.of(current));
        when(repository.findFirstByConfigKeyAndEffectiveAtLessThanEqualOrderByEffectiveAtDesc(anyString(),any())).thenReturn(Optional.of(current));
        when(repository.findLatestEffectiveByConfigKeys(any(),any())).thenReturn(List.of(current));
        var service=new OpsConfigService(repository,new AppConfigValueValidator(mapper),
                new PolicyValueRules(mock(KeycapRepository.class),mapper),config);
        TransactionSynchronizationManager.initSynchronization();
        try {
            service.change(KeycapPassivePolicyConfig.KEY_ENABLED,id.toString(),"true","test","activate");
            verify(config,never()).refresh();
            var callbacks=TransactionSynchronizationManager.getSynchronizations();
            callbacks.forEach(callback -> callback.afterCommit());
            verify(config).refresh();
            verify(repository,times(2)).save(any(AppConfig.class));
        } finally { TransactionSynchronizationManager.clearSynchronization(); }
    }
}
