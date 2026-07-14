package com.ggukmoney.beanzip.domain.onboarding.dto.mapper;

import com.ggukmoney.beanzip.domain.keycap.entity.Keycap;
import com.ggukmoney.beanzip.domain.onboarding.dto.response.OnboardingKeycapBoxOpenResponse;
import com.ggukmoney.beanzip.domain.onboarding.entity.OnboardingRewardAttempt;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Constructor;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class OnboardingRewardAttemptMapperTest {

    private final OnboardingRewardAttemptMapper mapper = Mappers.getMapper(OnboardingRewardAttemptMapper.class);

    @Test
    void mapsAttemptAndRewardKeycapToResponse() {
        UUID attemptId = UUID.randomUUID();
        UUID keycapId = UUID.randomUUID();
        Instant openedAt = Instant.parse("2026-07-15T01:00:05Z");
        Instant expiresAt = Instant.parse("2026-07-16T01:00:05Z");
        OnboardingRewardAttempt attempt = attempt(attemptId, keycapId, openedAt, expiresAt);

        OnboardingKeycapBoxOpenResponse response = mapper.mapToResponse(attempt);

        assertThat(response.onboardingAttemptId()).isEqualTo(attemptId);
        assertThat(response.keycapId()).isEqualTo(keycapId);
        assertThat(response.code()).isEqualTo("ONBOARDING_BASIC");
        assertThat(response.name()).isEqualTo("온보딩 키캡");
        assertThat(response.grade()).isEqualTo("COMMON");
        assertThat(response.imageUrl()).isEqualTo("https://example.com/keycaps/onboarding-basic.png");
        assertThat(response.soundUrl()).isEqualTo("https://example.com/keycaps/onboarding-basic.mp3");
        assertThat(response.completed()).isTrue();
        assertThat(response.rewardPoint()).isEqualTo(100);
        assertThat(response.openedAt()).isEqualTo(openedAt);
        assertThat(response.expiresAt()).isEqualTo(expiresAt);
    }

    @Test
    void responseDoesNotExposeInternalFields() {
        assertThat(OnboardingKeycapBoxOpenResponse.class.getRecordComponents())
                .extracting(component -> component.getName())
                .containsExactly(
                        "onboardingAttemptId",
                        "keycapId",
                        "code",
                        "name",
                        "grade",
                        "imageUrl",
                        "soundUrl",
                        "completed",
                        "rewardPoint",
                        "openedAt",
                        "expiresAt"
                );
    }

    private static OnboardingRewardAttempt attempt(UUID attemptId, UUID keycapId, Instant openedAt, Instant expiresAt) {
        Keycap keycap = newInstance(Keycap.class);
        ReflectionTestUtils.setField(keycap, "publicId", keycapId);
        ReflectionTestUtils.setField(keycap, "code", "ONBOARDING_BASIC");
        ReflectionTestUtils.setField(keycap, "name", "온보딩 키캡");
        ReflectionTestUtils.setField(keycap, "grade", Keycap.Grade.COMMON);
        ReflectionTestUtils.setField(keycap, "imageUrl", "https://example.com/keycaps/onboarding-basic.png");
        ReflectionTestUtils.setField(keycap, "soundUrl", "https://example.com/keycaps/onboarding-basic.mp3");

        OnboardingRewardAttempt attempt = newInstance(OnboardingRewardAttempt.class);
        ReflectionTestUtils.setField(attempt, "publicId", attemptId);
        ReflectionTestUtils.setField(attempt, "rewardKeycap", keycap);
        ReflectionTestUtils.setField(attempt, "rewardPointAmount", 100);
        ReflectionTestUtils.setField(attempt, "openedAt", openedAt);
        ReflectionTestUtils.setField(attempt, "expiresAt", expiresAt);
        return attempt;
    }

    private static <T> T newInstance(Class<T> type) {
        try {
            Constructor<T> constructor = type.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Failed to create test entity " + type.getSimpleName(), exception);
        }
    }
}
