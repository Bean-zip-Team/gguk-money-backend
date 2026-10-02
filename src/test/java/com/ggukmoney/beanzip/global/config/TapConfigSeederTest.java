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
}
