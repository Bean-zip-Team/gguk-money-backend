package com.ggukmoney.beanzip.domain.onboarding.dto.response;

import java.time.Instant;
import java.util.UUID;

public record OnboardingKeycapBoxOpenResponse(
        UUID onboardingAttemptId,
        UUID keycapId,
        String code,
        String name,
        String grade,
        String imageUrl,
        String soundUrl,
        boolean completed,
        int rewardPoint,
        Instant openedAt,
        Instant expiresAt
) {
}
