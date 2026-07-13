package com.ggukmoney.beanzip.global.config.service;

import com.ggukmoney.beanzip.global.config.TapPolicyConfig;
import com.ggukmoney.beanzip.global.config.dto.response.AppConfigResponse;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AppConfigServiceTest {

    private final TapPolicyConfig tapPolicyConfig = mock(TapPolicyConfig.class);
    private final AppConfigService appConfigService = new AppConfigService(tapPolicyConfig);

    @Test
    void returnsPublicTypedPoliciesFromTapPolicyConfig() {
        when(tapPolicyConfig.pointDailyCap()).thenReturn(20);
        when(tapPolicyConfig.boxDropBase()).thenReturn(200);
        when(tapPolicyConfig.boosterDurationSeconds()).thenReturn(300);
        when(tapPolicyConfig.boosterDailyLimit()).thenReturn(3);

        AppConfigResponse response = appConfigService.getAppConfig();

        assertThat(response.pointPolicy().dailyLimit()).isEqualTo(20);
        assertThat(response.boxPolicy().baseRequiredTapCount()).isEqualTo(200);
        assertThat(response.boosterPolicy().durationSeconds()).isEqualTo(300);
        assertThat(response.boosterPolicy().dailyLimit()).isEqualTo(3);
    }

    @Test
    void responseTypeDoesNotExposeInternalConfigShape() {
        assertThat(AppConfigResponse.class.getRecordComponents())
                .extracting(component -> component.getName())
                .containsExactly("pointPolicy", "boxPolicy", "boosterPolicy");

        assertThat(AppConfigResponse.BoxPolicy.class.getRecordComponents())
                .extracting(component -> component.getName())
                .containsExactly("baseRequiredTapCount");
    }
}
