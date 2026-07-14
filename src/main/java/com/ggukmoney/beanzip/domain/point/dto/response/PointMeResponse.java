package com.ggukmoney.beanzip.domain.point.dto.response;

public record PointMeResponse(
        long balance,
        long lifetimeEarned,
        long lifetimeSpent,
        boolean cashoutEligible,
        int minimumPoint,
        long estimatedKrw
) {
}
