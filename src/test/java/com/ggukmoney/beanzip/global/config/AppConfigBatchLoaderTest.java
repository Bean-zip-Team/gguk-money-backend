package com.ggukmoney.beanzip.global.config;

import com.ggukmoney.beanzip.global.config.entity.AppConfig;
import com.ggukmoney.beanzip.global.config.repository.AppConfigRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AppConfigBatchLoaderTest {

    private final AppConfigRepository repository = mock(AppConfigRepository.class);
    private final AppConfigBatchLoader loader = new AppConfigBatchLoader(repository);

    @Test
    void loadsRequestedKeysWithOneRepositoryCall() {
        Instant now = Instant.parse("2026-08-31T00:00:00Z");
        Set<String> keys = Set.of("tap.a", "tap.b");
        when(repository.findLatestEffectiveByConfigKeys(keys, now)).thenReturn(List.of(
                AppConfig.createFor("tap.a", "10", Instant.EPOCH),
                AppConfig.createFor("ignored", "99", Instant.EPOCH)
        ));

        assertThat(loader.load(keys, now)).isEqualTo(Map.of("tap.a", "10"));

        verify(repository).findLatestEffectiveByConfigKeys(keys, now);
    }

    @Test
    void skipsRepositoryForEmptyKeys() {
        assertThat(loader.load(Set.of(), Instant.EPOCH)).isEmpty();

        verify(repository, never()).findLatestEffectiveByConfigKeys(Set.of(), Instant.EPOCH);
    }

    @Test
    void rejectsDuplicateRows() {
        Instant now = Instant.parse("2026-08-31T00:00:00Z");
        Set<String> keys = Set.of("tap.a");
        when(repository.findLatestEffectiveByConfigKeys(keys, now)).thenReturn(List.of(
                AppConfig.createFor("tap.a", "10", Instant.EPOCH),
                AppConfig.createFor("tap.a", "20", Instant.EPOCH)
        ));

        assertThatThrownBy(() -> loader.load(keys, now))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("tap.a");
    }
}
