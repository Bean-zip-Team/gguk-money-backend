package com.ggukmoney.beanzip.global.config;

import com.ggukmoney.beanzip.global.config.entity.AppConfig;
import com.ggukmoney.beanzip.global.config.repository.AppConfigRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Collection;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KeycapBoxPolicyConfigTest {

    private final AppConfigRepository appConfigRepository = mock(AppConfigRepository.class);
    private final KeycapBoxPolicyConfig config = new KeycapBoxPolicyConfig(new AppConfigBatchLoader(appConfigRepository));

    @BeforeEach
    void adaptExistingSingleKeyFixturesToBatchQuery() {
        when(appConfigRepository.findLatestEffectiveByConfigKeys(any(), any(Instant.class)))
                .thenAnswer(invocation -> {
                    Collection<String> keys = invocation.getArgument(0);
                    Instant now = invocation.getArgument(1);
                    return keys.stream()
                            .map(key -> appConfigRepository
                                    .findFirstByConfigKeyAndEffectiveAtLessThanEqualOrderByEffectiveAtDesc(key, now))
                            .flatMap(Optional::stream)
                            .toList();
                });
    }

    @Test
    void managesOnlyDrawPriceKey() {
        // 상자 개봉 주기·무료/광고/일괄 개봉 한도는 개봉과 함께 사라졌다 (BEA-329).
        assertThat(KeycapBoxPolicyConfig.DEFAULT_VALUES)
                .containsOnlyKeys(KeycapBoxPolicyConfig.KEY_DRAW_PRICE)
                .containsEntry(KeycapBoxPolicyConfig.KEY_DRAW_PRICE, "5");
        assertThat(KeycapBoxPolicyConfig.KEY_DRAW_PRICE).isEqualTo("keycap.draw.price");
    }

    @Test
    void usesDefaultWhenRowIsMissing() {
        when(appConfigRepository.findFirstByConfigKeyAndEffectiveAtLessThanEqualOrderByEffectiveAtDesc(any(), any()))
                .thenReturn(Optional.empty());

        config.refresh();

        assertThat(config.drawPrice()).isEqualTo(5);
        verify(appConfigRepository).findLatestEffectiveByConfigKeys(
                eq(KeycapBoxPolicyConfig.DEFAULT_VALUES.keySet()),
                any(Instant.class)
        );
    }

    @Test
    void resolvesDrawPriceFromAppConfigRow() {
        stubDrawPrice("3");

        config.refresh();

        assertThat(config.drawPrice()).isEqualTo(3);
    }

    @Test
    void keepsLastKnownGoodValueWhenNewValueIsInvalid() {
        stubDrawPrice("3");
        config.refresh();

        stubDrawPrice("0");
        config.refresh();
        assertThat(config.drawPrice()).isEqualTo(3);

        stubDrawPrice("five");
        config.refresh();
        assertThat(config.drawPrice()).isEqualTo(3);
    }

    @Test
    void fallsBackToDefaultWhenValueIsInvalidOnInitialLoad() {
        stubDrawPrice("-1");

        config.refresh();

        assertThat(config.drawPrice()).isEqualTo(5);
    }

    private void stubDrawPrice(String value) {
        when(appConfigRepository.findFirstByConfigKeyAndEffectiveAtLessThanEqualOrderByEffectiveAtDesc(
                eq(KeycapBoxPolicyConfig.KEY_DRAW_PRICE), any(Instant.class)
        )).thenReturn(Optional.of(AppConfig.createFor(KeycapBoxPolicyConfig.KEY_DRAW_PRICE, value, Instant.EPOCH)));
    }
}
