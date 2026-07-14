package com.ggukmoney.beanzip.domain.onboarding.service;

import com.ggukmoney.beanzip.domain.onboarding.dto.request.OnboardingKeycapBoxOpenRequest;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OnboardingTapValidatorTest {

    private final OnboardingTapValidator validator = new OnboardingTapValidator();

    @Test
    void acceptsExactlyFortyFiveSequentialNonDecreasingTapEvents() {
        int acceptedTapCount = validator.validateCompleted(request(events(45)));

        assertThat(acceptedTapCount).isEqualTo(45);
    }

    @Test
    void rejectsTapCountThatIsNotExactlyFortyFive() {
        assertReason(events(44), "ONBOARDING_TAP_NOT_COMPLETED");
        assertReason(events(46), "ONBOARDING_TAP_NOT_COMPLETED");
        assertReason(List.of(), "ONBOARDING_TAP_NOT_COMPLETED");
    }

    @Test
    void rejectsDuplicateMissingAndOutOfRangeSequence() {
        List<OnboardingKeycapBoxOpenRequest.TapEvent> duplicate = new ArrayList<>(events(45));
        duplicate.set(44, new OnboardingKeycapBoxOpenRequest.TapEvent(44, occurredAt(45)));
        assertReason(duplicate, "ONBOARDING_TAP_INVALID");

        List<OnboardingKeycapBoxOpenRequest.TapEvent> missing = new ArrayList<>(events(45));
        missing.set(44, new OnboardingKeycapBoxOpenRequest.TapEvent(46, occurredAt(45)));
        assertReason(missing, "ONBOARDING_TAP_INVALID");

        List<OnboardingKeycapBoxOpenRequest.TapEvent> zero = new ArrayList<>(events(45));
        zero.set(0, new OnboardingKeycapBoxOpenRequest.TapEvent(0, occurredAt(1)));
        assertReason(zero, "ONBOARDING_TAP_INVALID");
    }

    @Test
    void rejectsNullOccurredAtAndDecreasingOccurredAt() {
        List<OnboardingKeycapBoxOpenRequest.TapEvent> nullOccurredAt = new ArrayList<>(events(45));
        nullOccurredAt.set(0, new OnboardingKeycapBoxOpenRequest.TapEvent(1, null));
        assertReason(nullOccurredAt, "ONBOARDING_TAP_INVALID");

        List<OnboardingKeycapBoxOpenRequest.TapEvent> decreasing = new ArrayList<>(events(45));
        decreasing.set(2, new OnboardingKeycapBoxOpenRequest.TapEvent(3, occurredAt(1)));
        assertReason(decreasing, "ONBOARDING_TAP_INVALID");
    }

    private void assertReason(List<OnboardingKeycapBoxOpenRequest.TapEvent> events, String reason) {
        assertThatThrownBy(() -> validator.validateCompleted(request(events)))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(exception -> ((ResponseStatusException) exception).getReason())
                .isEqualTo(reason);
    }

    private static OnboardingKeycapBoxOpenRequest request(List<OnboardingKeycapBoxOpenRequest.TapEvent> events) {
        return new OnboardingKeycapBoxOpenRequest(UUID.randomUUID(), events);
    }

    private static List<OnboardingKeycapBoxOpenRequest.TapEvent> events(int count) {
        List<OnboardingKeycapBoxOpenRequest.TapEvent> events = new ArrayList<>();
        for (int sequence = 1; sequence <= count; sequence++) {
            events.add(new OnboardingKeycapBoxOpenRequest.TapEvent(sequence, occurredAt(sequence)));
        }
        return events;
    }

    private static Instant occurredAt(int sequence) {
        return Instant.parse("2026-07-15T01:00:00Z").plusMillis(sequence);
    }
}
