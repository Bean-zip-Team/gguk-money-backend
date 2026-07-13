package com.ggukmoney.beanzip.global.config.dto.response;

public record AppConfigResponse(
        PointPolicy pointPolicy,
        BoxPolicy boxPolicy,
        BoosterPolicy boosterPolicy
) {

    public record PointPolicy(
            int dailyLimit
    ) {
    }

    public record BoxPolicy(
            int baseRequiredTapCount
    ) {
    }

    public record BoosterPolicy(
            int durationSeconds,
            int dailyLimit
    ) {
    }
}
