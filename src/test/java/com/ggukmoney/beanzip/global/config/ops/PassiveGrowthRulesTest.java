package com.ggukmoney.beanzip.global.config.ops;
import com.ggukmoney.beanzip.domain.keycap.repository.KeycapRepository;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
class PassiveGrowthRulesTest {
    private final PolicyValueRules rules=new PolicyValueRules(mock(KeycapRepository.class),new ObjectMapper());
    @Test void ratioAcceptsDecimalsAndLegacyStrengthControlsAreReadOnly() {
        assertThat(rules.find("keycap.passive.COMMON.startRatio").orElseThrow().accepts("0.5")).isTrue();
        assertThat(rules.find("keycap.passive.COMMON.startRatio").orElseThrow().accepts("1.01")).isFalse();
        assertThat(rules.find("keycap.passive.COMMON.maxStrength").orElseThrow().accepts("0.5")).isFalse();
    }
}
