package com.ggukmoney.beanzip.global.config.ops;
import com.ggukmoney.beanzip.global.config.*;
import com.ggukmoney.beanzip.global.config.entity.AppConfig;
import com.ggukmoney.beanzip.global.config.repository.AppConfigRepository;
import com.ggukmoney.beanzip.domain.keycap.repository.KeycapRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
class EconomyActivationServiceTest {
    private final AppConfigRepository repository=mock(AppConfigRepository.class);
    private final ObjectMapper mapper=new ObjectMapper();
    private final OpsConfigService service=new OpsConfigService(repository,new AppConfigValueValidator(mapper),new PolicyValueRules(mock(KeycapRepository.class),mapper),mock(KeycapPassivePolicyConfig.class),mock(KeycapBoxPolicyConfig.class));
    private final String key=KeycapBoxPolicyConfig.KEY_SHARD_ENABLED;
    @Test void missingSeedIsExplainedAndActivationIsNotSaved(){
        assertThatThrownBy(()->service.change(key,"missing","true","user","activate"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("시더 상태");
        verify(repository,never()).save(any());
    }
    @Test void effectiveActivationCannotBeChangedEvenIfLatestValueIsFalse(){
        var row=AppConfig.createFor(key,"false",Instant.EPOCH);
        when(repository.findFirstByConfigKeyOrderByIdAsc(key)).thenReturn(Optional.of(row));
        when(repository.findLatestEffectiveWithHistory(any(),eq(key),any())).thenReturn(List.of(AppConfig.createFor(key,"true",Instant.EPOCH),row));
        assertThatThrownBy(()->service.change(key,"any","false","user","undo"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("이미 경제가 전환");
        verify(repository,never()).save(any());
    }
    @Test void activationLocksSeedBeforeReadingCurrentVersionAndSaving(){
        var row=AppConfig.createFor(key,"false",Instant.EPOCH); var id=UUID.randomUUID(); ReflectionTestUtils.setField(row,"publicId",id);
        when(repository.findFirstByConfigKeyOrderByIdAsc(key)).thenReturn(Optional.of(row));
        when(repository.findLatestEffectiveWithHistory(any(),eq(key),any())).thenReturn(List.of(row));
        when(repository.findFirstByConfigKeyAndEffectiveAtLessThanEqualOrderByEffectiveAtDesc(eq(key),any())).thenReturn(Optional.of(row));
        service.change(key,id.toString(),"true","user","activate");
        var order=inOrder(repository);
        order.verify(repository).findFirstByConfigKeyOrderByIdAsc(key);
        order.verify(repository).findLatestEffectiveWithHistory(any(),eq(key),any());
        order.verify(repository).findFirstByConfigKeyAndEffectiveAtLessThanEqualOrderByEffectiveAtDesc(eq(key),any());
        order.verify(repository).save(argThat(value->"true".equals(value.getConfigValue())));
    }
}
