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
        when(tapPolicyConfig.pointDailyCap()).thenReturn(150);
        when(tapPolicyConfig.boxSessionStep1()).thenReturn(25);
        when(tapPolicyConfig.boxSessionStep2()).thenReturn(35);
        when(tapPolicyConfig.boxSessionStep3()).thenReturn(50);
        when(tapPolicyConfig.boxSessionStep4()).thenReturn(70);
        when(tapPolicyConfig.boxSessionStep5()).thenReturn(100);
        when(tapPolicyConfig.boxSessionTailStep()).thenReturn(180);
        when(tapPolicyConfig.boxSessionMaxDurationSeconds()).thenReturn(3600);
        when(tapPolicyConfig.boosterDurationSeconds()).thenReturn(300);
        when(tapPolicyConfig.boosterDailyLimit()).thenReturn(3);

        AppConfigResponse response = appConfigService.getAppConfig();

        assertThat(response.pointPolicy().dailyLimit()).isEqualTo(150);
        assertThat(response.boxPolicy().sessionStepTapCounts()).containsExactly(25, 35, 50, 70, 100);
        assertThat(response.boxPolicy().tailStepTapCount()).isEqualTo(180);
        assertThat(response.boxPolicy().sessionMaxDurationSeconds()).isEqualTo(3600);
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
                .containsExactly("sessionStepTapCounts", "tailStepTapCount", "sessionMaxDurationSeconds");
    }
}
