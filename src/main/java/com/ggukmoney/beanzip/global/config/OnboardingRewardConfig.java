package com.ggukmoney.beanzip.global.config;

import com.ggukmoney.beanzip.global.config.repository.AppConfigRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;

@Component
@RequiredArgsConstructor
public class OnboardingRewardConfig {

    public static final String KEY_REWARD_KEYCAP_CODE = "onboarding.reward.keycapCode";
    public static final String KEY_REWARD_POINT_AMOUNT = "onboarding.reward.pointAmount";
    public static final String KEY_ATTEMPT_TTL_SECONDS = "onboarding.reward.attemptTtlSeconds";

    private final AppConfigRepository appConfigRepository;

    public OnboardingRewardPolicy resolve() {
        try {
            String keycapCode = stripJsonString(resolveValue(KEY_REWARD_KEYCAP_CODE));
            int pointAmount = Integer.parseInt(resolveValue(KEY_REWARD_POINT_AMOUNT).trim());
            long ttlSeconds = Long.parseLong(resolveValue(KEY_ATTEMPT_TTL_SECONDS).trim());
            if (!StringUtils.hasText(keycapCode) || pointAmount < 0 || ttlSeconds <= 0) {
                throw unavailable();
            }
            return new OnboardingRewardPolicy(keycapCode, pointAmount, Duration.ofSeconds(ttlSeconds));
        } catch (RuntimeException exception) {
            if (exception instanceof ResponseStatusException responseStatusException) {
                throw responseStatusException;
            }
            throw unavailable();
        }
    }

    private String resolveValue(String key) {
        return appConfigRepository.findFirstByConfigKeyAndEffectiveAtLessThanEqualOrderByEffectiveAtDesc(key, Instant.now())
                .orElseThrow(this::unavailable)
                .getConfigValue();
    }

    private String stripJsonString(String value) {
        String trimmed = value.trim();
        if (trimmed.length() >= 2 && trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
            return trimmed.substring(1, trimmed.length() - 1);
        }
        return trimmed;
    }

    private ResponseStatusException unavailable() {
        return new ResponseStatusException(HttpStatus.CONFLICT, "ONBOARDING_REWARD_NOT_AVAILABLE");
    }

    public record OnboardingRewardPolicy(
            String rewardKeycapCode,
            int rewardPointAmount,
            Duration attemptTtl
    ) {
    }
}
