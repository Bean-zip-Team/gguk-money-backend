package com.ggukmoney.beanzip.global.config;

import com.ggukmoney.beanzip.global.config.entity.AppConfig;
import com.ggukmoney.beanzip.global.config.repository.AppConfigRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RankChangeNotificationPolicyConfigTest {

    private final AppConfigRepository repository = mock(AppConfigRepository.class);
    private final RankChangeNotificationPolicyConfig config =
            new RankChangeNotificationPolicyConfig(new AppConfigBatchLoader(repository));

    @Test
    void defaultsToOneRankWhenNoAppConfigRowExists() {
        assertThat(config.minimumDifference()).isEqualTo(1);
    }

    @Test
    void refreshesFromTheLatestEffectiveAppConfigValue() {
        when(repository.findLatestEffectiveByConfigKeys(
                eq(Set.of(RankChangeNotificationPolicyConfig.KEY_MINIMUM_DIFFERENCE)), any(Instant.class)
        )).thenReturn(List.of(AppConfig.createFor(
                RankChangeNotificationPolicyConfig.KEY_MINIMUM_DIFFERENCE, "4", Instant.EPOCH)));

        config.refresh();

        assertThat(config.minimumDifference()).isEqualTo(4);
    }

    @Test
    void invalidRefreshRetainsLastKnownGoodValue() {
        when(repository.findLatestEffectiveByConfigKeys(
                eq(Set.of(RankChangeNotificationPolicyConfig.KEY_MINIMUM_DIFFERENCE)), any(Instant.class)
        )).thenReturn(List.of(AppConfig.createFor(
                        RankChangeNotificationPolicyConfig.KEY_MINIMUM_DIFFERENCE, "4", Instant.EPOCH)))
                .thenReturn(List.of(AppConfig.createFor(
                        RankChangeNotificationPolicyConfig.KEY_MINIMUM_DIFFERENCE, "0", Instant.EPOCH)));

        config.refresh();
        config.refresh();

        assertThat(config.minimumDifference()).isEqualTo(4);
    }

    @Test
    void missingRefreshRetainsLastKnownGoodValue() {
        when(repository.findLatestEffectiveByConfigKeys(
                eq(Set.of(RankChangeNotificationPolicyConfig.KEY_MINIMUM_DIFFERENCE)), any(Instant.class)
        )).thenReturn(List.of(AppConfig.createFor(
                        RankChangeNotificationPolicyConfig.KEY_MINIMUM_DIFFERENCE, "4", Instant.EPOCH)))
                .thenReturn(List.of());

        config.refresh();
        config.refresh();

        assertThat(config.minimumDifference()).isEqualTo(4);
    }

    @Test
    void malformedRefreshRetainsLastKnownGoodValue() {
        when(repository.findLatestEffectiveByConfigKeys(
                eq(Set.of(RankChangeNotificationPolicyConfig.KEY_MINIMUM_DIFFERENCE)), any(Instant.class)
        )).thenReturn(List.of(AppConfig.createFor(
                        RankChangeNotificationPolicyConfig.KEY_MINIMUM_DIFFERENCE, "4", Instant.EPOCH)))
                .thenReturn(List.of(AppConfig.createFor(
                        RankChangeNotificationPolicyConfig.KEY_MINIMUM_DIFFERENCE, "not-an-integer", Instant.EPOCH)));

        config.refresh();
        config.refresh();

        assertThat(config.minimumDifference()).isEqualTo(4);
    }

    @Test
    void repositoryFailureRetainsLastKnownGoodValue() {
        when(repository.findLatestEffectiveByConfigKeys(
                eq(Set.of(RankChangeNotificationPolicyConfig.KEY_MINIMUM_DIFFERENCE)), any(Instant.class)
        )).thenReturn(List.of(AppConfig.createFor(
                RankChangeNotificationPolicyConfig.KEY_MINIMUM_DIFFERENCE, "4", Instant.EPOCH)))
                .thenThrow(new IllegalStateException("database unavailable"));

        config.refresh();
        config.refresh();

        assertThat(config.minimumDifference()).isEqualTo(4);
    }
}
