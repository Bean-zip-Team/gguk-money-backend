package com.ggukmoney.beanzip.global.config.service;

import com.ggukmoney.beanzip.global.config.TapPolicyConfig;
import com.ggukmoney.beanzip.global.config.dto.response.AppConfigResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AppConfigService {

    private final TapPolicyConfig tapPolicyConfig;

    public AppConfigResponse getAppConfig() {
        return new AppConfigResponse(
                new AppConfigResponse.PointPolicy(tapPolicyConfig.pointDailyCap()),
                new AppConfigResponse.BoxPolicy(
                        List.of(
                                tapPolicyConfig.boxSessionStep1(),
                                tapPolicyConfig.boxSessionStep2(),
                                tapPolicyConfig.boxSessionStep3(),
                                tapPolicyConfig.boxSessionStep4(),
                                tapPolicyConfig.boxSessionStep5()
                        ),
                        tapPolicyConfig.boxSessionTailStep(),
                        tapPolicyConfig.boxSessionIdleTimeoutSeconds()
                ),
                new AppConfigResponse.BoosterPolicy(
                        tapPolicyConfig.boosterDurationSeconds(),
                        tapPolicyConfig.boosterDailyLimit()
                )
        );
    }
}
