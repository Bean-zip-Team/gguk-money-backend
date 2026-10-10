package com.ggukmoney.beanzip.global.config;

import com.ggukmoney.beanzip.global.config.entity.AppConfig;
import com.ggukmoney.beanzip.global.config.repository.AppConfigRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TapConfigSeederTest {

    private static final String KEY = TapPolicyConfig.KEY_POINT_DAILY_CAP;

    private final AppConfigRepository repository = mock(AppConfigRepository.class);

    @Test
    void revertsAnOverriddenValueWhenRevertIsOn() {
        overridden();

        new TapConfigSeeder(repository, true).run();

        verify(repository).save(argThat(row -> KEY.equals(row.getConfigKey())));
    }

    @Test
    void keepsAnOverriddenValueWhenRevertIsOff_theDefault() {
        overridden();

        new TapConfigSeeder(repository, false).run();

        verify(repository, never()).save(argThat(row -> KEY.equals(row.getConfigKey())));
    }

    @Test
    void stillSeedsMissingKeysWhenRevertIsOff() {
        when(repository.findDistinctConfigKeys()).thenReturn(List.of());

        new TapConfigSeeder(repository, false).run();

        verify(repository).save(argThat(row -> KEY.equals(row.getConfigKey())));
    }

    private void overridden() {
        when(repository.findDistinctConfigKeys()).thenReturn(List.of());
        when(repository.findFirstByConfigKeyAndEffectiveAtLessThanEqualOrderByEffectiveAtDesc(eq(KEY), any(Instant.class)))
                .thenReturn(Optional.of(AppConfig.createFor(KEY, "999", Instant.EPOCH)));
    }

    @Test void migratingToStartRatioPreservesLegacyOperationalGrowth() {
        String prefix="keycap.passive.COMMON.";
        when(repository.findFirstByConfigKeyAndEffectiveAtLessThanEqualOrderByEffectiveAtDesc(eq(prefix+"startStrength"),any()))
                .thenReturn(Optional.of(AppConfig.createFor(prefix+"startStrength","0.03",Instant.EPOCH)));
        when(repository.findFirstByConfigKeyAndEffectiveAtLessThanEqualOrderByEffectiveAtDesc(eq(prefix+"maxStrength"),any()))
                .thenReturn(Optional.of(AppConfig.createFor(prefix+"maxStrength","0.1",Instant.EPOCH)));
        new TapConfigSeeder(repository,true).run();
        verify(repository).save(argThat(row -> (prefix+"startRatio").equals(row.getConfigKey())
                && Math.abs(Double.parseDouble(row.getConfigValue())-0.3)<1e-12));
        verify(repository,never()).save(argThat(row -> row.getConfigKey().endsWith("Strength")));
    }

    @Test void explicitOperationalStartRatioIsNeverResetBySeeder() {
        String key="keycap.passive.COMMON.startRatio";
        when(repository.findFirstByConfigKeyAndEffectiveAtLessThanEqualOrderByEffectiveAtDesc(eq(key),any()))
                .thenReturn(Optional.of(AppConfig.createFor(key,"0.7",Instant.EPOCH)));
        new TapConfigSeeder(repository,true).run();
        verify(repository,never()).save(argThat(row -> key.equals(row.getConfigKey())));
    }
}
