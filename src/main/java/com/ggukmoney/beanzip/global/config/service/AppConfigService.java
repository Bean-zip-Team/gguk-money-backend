package com.ggukmoney.beanzip.global.config.service;

import com.ggukmoney.beanzip.global.config.TapPolicyConfig;
import com.ggukmoney.beanzip.global.config.dto.response.AppConfigResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AppConfigService {

    private final TapPolicyConfig tapPolicyConfig;

    public AppConfigResponse getAppConfig() {
        return new AppConfigResponse(
                new AppConfigResponse.PointPolicy(tapPolicyConfig.pointDailyCap()),
                new AppConfigResponse.BoxPolicy(tapPolicyConfig.boxDropBase()),
                new AppConfigResponse.BoosterPolicy(
                        tapPolicyConfig.boosterDurationSeconds(),
                        tapPolicyConfig.boosterDailyLimit()
                )
        );
    }
}
