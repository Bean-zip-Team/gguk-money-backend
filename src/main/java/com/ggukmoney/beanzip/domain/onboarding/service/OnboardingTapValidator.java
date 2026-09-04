package com.ggukmoney.beanzip.domain.onboarding.service;

import com.ggukmoney.beanzip.domain.onboarding.dto.request.OnboardingKeycapBoxOpenRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
public class OnboardingTapValidator {

    private static final int REQUIRED_TAP_COUNT = 45;

    public int validateCompleted(OnboardingKeycapBoxOpenRequest request) {
        if (request == null || request.tapEvents() == null || request.tapEvents().size() != REQUIRED_TAP_COUNT) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ONBOARDING_TAP_NOT_COMPLETED");
        }

        List<OnboardingKeycapBoxOpenRequest.TapEvent> sortedEvents = request.tapEvents().stream()
                .sorted(Comparator.comparing(OnboardingKeycapBoxOpenRequest.TapEvent::sequence,
                        Comparator.nullsFirst(Integer::compareTo)))
                .toList();
        Set<Integer> sequences = new HashSet<>();
        Instant previousOccurredAt = null;
        for (int expectedSequence = 1; expectedSequence <= REQUIRED_TAP_COUNT; expectedSequence++) {
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
        return REQUIRED_TAP_COUNT;
    }
}
