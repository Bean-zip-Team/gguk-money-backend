package com.ggukmoney.beanzip.domain.onboarding.service;

import com.ggukmoney.beanzip.domain.onboarding.dto.request.OnboardingKeycapBoxOpenRequest;
import com.ggukmoney.beanzip.global.config.OnboardingRewardConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.IntSupplier;

@Component
public class OnboardingTapValidator {

    private final IntSupplier requiredTapCount;

    @Autowired
    public OnboardingTapValidator(OnboardingRewardConfig rewardConfig) {
        this(() -> rewardConfig.resolve().requiredTapCount());
    }

    OnboardingTapValidator(IntSupplier requiredTapCount) {
        this.requiredTapCount = requiredTapCount;
    }

    public int validateCompleted(OnboardingKeycapBoxOpenRequest request) {
        if (request == null || request.tapEvents() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ONBOARDING_TAP_NOT_COMPLETED");
        }
        int required = requiredTapCount.getAsInt();
        if (request.tapEvents().size() != required) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ONBOARDING_TAP_NOT_COMPLETED");
        }

        List<OnboardingKeycapBoxOpenRequest.TapEvent> sortedEvents = request.tapEvents().stream()
                .sorted(Comparator.comparing(OnboardingKeycapBoxOpenRequest.TapEvent::sequence,
                        Comparator.nullsFirst(Integer::compareTo)))
                .toList();
        Set<Integer> sequences = new HashSet<>();
        Instant previousOccurredAt = null;
        for (int expectedSequence = 1; expectedSequence <= required; expectedSequence++) {
            OnboardingKeycapBoxOpenRequest.TapEvent event = sortedEvents.get(expectedSequence - 1);
            if (event.sequence() == null
                    || event.sequence() != expectedSequence
                    || !sequences.add(event.sequence())
                    || event.occurredAt() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ONBOARDING_TAP_INVALID");
            }
            if (previousOccurredAt != null && event.occurredAt().isBefore(previousOccurredAt)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ONBOARDING_TAP_INVALID");
            }
            previousOccurredAt = event.occurredAt();
        }
        return required;
    }
}
