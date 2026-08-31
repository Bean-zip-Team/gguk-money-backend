package com.ggukmoney.beanzip.global.config;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CashoutPolicyConfigTest {

    private final AppConfigBatchLoader batchLoader = mock(AppConfigBatchLoader.class);
    private final CashoutPolicyConfig config = new CashoutPolicyConfig(batchLoader);

    @Test
    void refreshesTwoKeysWithOneBatchLoad() {
        when(batchLoader.load(eq(CashoutPolicyConfig.DEFAULT_VALUES.keySet()), any(Instant.class)))
                .thenReturn(Map.of(
                        CashoutPolicyConfig.KEY_MINIMUM_POINT, "100",
                        CashoutPolicyConfig.KEY_POINT_TO_KRW_RATE, "0.5"
                ));

        config.refresh();

        assertThat(config.minimumPoint()).isEqualTo(100);
        assertThat(config.pointToKrwRate()).isEqualByComparingTo(new BigDecimal("0.5"));
        verify(batchLoader).load(eq(CashoutPolicyConfig.DEFAULT_VALUES.keySet()), any(Instant.class));
    }

    @Test
    void keepsInvalidValueWhileApplyingOtherValidValue() {
        when(batchLoader.load(eq(CashoutPolicyConfig.DEFAULT_VALUES.keySet()), any(Instant.class)))
                .thenReturn(Map.of(
                        CashoutPolicyConfig.KEY_MINIMUM_POINT, "0",
                        CashoutPolicyConfig.KEY_POINT_TO_KRW_RATE, "0.5"
                ));

        config.refresh();

        assertThat(config.minimumPoint()).isEqualTo(50);
        assertThat(config.pointToKrwRate()).isEqualByComparingTo(new BigDecimal("0.5"));
    }

    @Test
    void keepsPerKeyLastKnownGoodWhenNextValueIsInvalid() {
        when(batchLoader.load(eq(CashoutPolicyConfig.DEFAULT_VALUES.keySet()), any(Instant.class)))
                .thenReturn(
                        Map.of(CashoutPolicyConfig.KEY_MINIMUM_POINT, "100"),
                        Map.of(
                                CashoutPolicyConfig.KEY_MINIMUM_POINT, "bad",
                                CashoutPolicyConfig.KEY_POINT_TO_KRW_RATE, "0.25"
                        )
                );

        config.refresh();
        config.refresh();

        assertThat(config.minimumPoint()).isEqualTo(100);
        assertThat(config.pointToKrwRate()).isEqualByComparingTo(new BigDecimal("0.25"));
    }

    @Test
    void keepsWholeSnapshotWhenDatabaseFails() {
        when(batchLoader.load(eq(CashoutPolicyConfig.DEFAULT_VALUES.keySet()), any(Instant.class)))
                .thenReturn(Map.of(CashoutPolicyConfig.KEY_MINIMUM_POINT, "100"))
                .thenThrow(new RuntimeException("database unavailable"));

        config.refresh();
        config.refresh();

        assertThat(config.minimumPoint()).isEqualTo(100);
        verify(batchLoader, times(2)).load(eq(CashoutPolicyConfig.DEFAULT_VALUES.keySet()), any(Instant.class));
    }
}
