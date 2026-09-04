package com.ggukmoney.beanzip.domain.onboarding.service;

import com.ggukmoney.beanzip.domain.onboarding.dto.request.OnboardingKeycapBoxOpenRequest;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class OnboardingKeycapBoxOpenRequestHasherTest {

    private final OnboardingKeycapBoxOpenRequestHasher hasher = new OnboardingKeycapBoxOpenRequestHasher();

    @Test
    void normalizesTapEventsBySequenceAndInstantString() {
        UUID tapSessionId = UUID.randomUUID();
        List<OnboardingKeycapBoxOpenRequest.TapEvent> events = events();
        List<OnboardingKeycapBoxOpenRequest.TapEvent> shuffled = new ArrayList<>(events);
        shuffled.sort(Comparator.comparing(OnboardingKeycapBoxOpenRequest.TapEvent::sequence).reversed());

        String hash = hasher.hash(new OnboardingKeycapBoxOpenRequest(tapSessionId, events));
        String shuffledHash = hasher.hash(new OnboardingKeycapBoxOpenRequest(tapSessionId, shuffled));
        String otherHash = hasher.hash(new OnboardingKeycapBoxOpenRequest(UUID.randomUUID(), events));

        assertThat(hash).isEqualTo(shuffledHash);
        assertThat(hash).isNotEqualTo(otherHash);
    }

    private static List<OnboardingKeycapBoxOpenRequest.TapEvent> events() {
        List<OnboardingKeycapBoxOpenRequest.TapEvent> events = new ArrayList<>();
        for (int sequence = 1; sequence <= 45; sequence++) {
            events.add(new OnboardingKeycapBoxOpenRequest.TapEvent(
                    sequence,
                    Instant.parse("2026-07-15T01:00:00Z").plusMillis(sequence)
            ));
        }
        return events;
    }
}
