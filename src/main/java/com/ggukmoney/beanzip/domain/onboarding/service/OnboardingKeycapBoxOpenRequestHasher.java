package com.ggukmoney.beanzip.domain.onboarding.service;

import com.ggukmoney.beanzip.domain.onboarding.dto.request.OnboardingKeycapBoxOpenRequest;
import com.ggukmoney.beanzip.global.util.TokenHash;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.stream.Collectors;

@Component
public class OnboardingKeycapBoxOpenRequestHasher {

    public String hash(OnboardingKeycapBoxOpenRequest request) {
        String normalizedEvents = request.tapEvents().stream()
                .sorted(Comparator.comparing(OnboardingKeycapBoxOpenRequest.TapEvent::sequence))
                .map(event -> event.sequence() + "@" + event.occurredAt().toString())
                .collect(Collectors.joining(","));
        return TokenHash.sha256Base64Url("tapSessionId=%s;tapEvents=%s".formatted(
                request.tapSessionId(),
                normalizedEvents
        ));
    }
}
