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
    void mapsAttemptAndBothRewardKeycapsToResponse() {
        UUID attemptId = UUID.randomUUID();
        UUID mainKeycapId = UUID.randomUUID();
        UUID bonusKeycapId = UUID.randomUUID();
        Instant openedAt = Instant.parse("2026-07-15T01:00:05Z");
        Instant expiresAt = Instant.parse("2026-07-15T01:15:05Z");
        OnboardingRewardAttempt attempt = attempt(attemptId, mainKeycapId, bonusKeycapId, openedAt, expiresAt);

        OnboardingKeycapBoxOpenResponse response = mapper.mapToResponse(attempt);

        assertThat(response.onboardingAttemptId()).isEqualTo(attemptId);
        assertThat(response.keycaps()).hasSize(2);
        assertThat(response.keycaps().get(0).keycapId()).isEqualTo(mainKeycapId);
        assertThat(response.keycaps().get(0).code()).isEqualTo("main");
        assertThat(response.keycaps().get(0).name()).isEqualTo("메인 키캡");
        assertThat(response.keycaps().get(0).grade()).isEqualTo("COMMON");
        assertThat(response.keycaps().get(0).imageUrl()).isEqualTo("https://example.com/keycaps/main.webp");
        assertThat(response.keycaps().get(0).soundUrl()).isEqualTo("https://example.com/keycaps/main.mp3");
        assertThat(response.keycaps().get(1).keycapId()).isEqualTo(bonusKeycapId);
        assertThat(response.keycaps().get(1).code()).isEqualTo("cheer");
        assertThat(response.completed()).isTrue();
        assertThat(response.rewardPoint()).isEqualTo(2);
        assertThat(response.openedAt()).isEqualTo(openedAt);
        assertThat(response.expiresAt()).isEqualTo(expiresAt);
    }

    @Test
    void responseDoesNotExposeInternalFields() {
        assertThat(OnboardingKeycapBoxOpenResponse.class.getRecordComponents())
                .extracting(component -> component.getName())
                .containsExactly(
                        "onboardingAttemptId",
                        "keycaps",
                        "completed",
                        "rewardPoint",
                        "openedAt",
                        "expiresAt"
                );
    }

    private static OnboardingRewardAttempt attempt(
            UUID attemptId,
            UUID mainKeycapId,
            UUID bonusKeycapId,
            Instant openedAt,
            Instant expiresAt
    ) {
        Keycap mainKeycap = newInstance(Keycap.class);
        ReflectionTestUtils.setField(mainKeycap, "publicId", mainKeycapId);
        ReflectionTestUtils.setField(mainKeycap, "code", "main");
        ReflectionTestUtils.setField(mainKeycap, "name", "메인 키캡");
        ReflectionTestUtils.setField(mainKeycap, "grade", Keycap.Grade.COMMON);
        ReflectionTestUtils.setField(mainKeycap, "imageUrl", "https://example.com/keycaps/main.webp");
        ReflectionTestUtils.setField(mainKeycap, "soundUrl", "https://example.com/keycaps/main.mp3");

        Keycap bonusKeycap = newInstance(Keycap.class);
        ReflectionTestUtils.setField(bonusKeycap, "publicId", bonusKeycapId);
        ReflectionTestUtils.setField(bonusKeycap, "code", "cheer");
        ReflectionTestUtils.setField(bonusKeycap, "name", "치어 키캡");
        ReflectionTestUtils.setField(bonusKeycap, "grade", Keycap.Grade.COMMON);
        ReflectionTestUtils.setField(bonusKeycap, "imageUrl", "https://example.com/keycaps/cheer.webp");
        ReflectionTestUtils.setField(bonusKeycap, "soundUrl", "https://example.com/keycaps/cheer.mp3");

        OnboardingRewardAttempt attempt = newInstance(OnboardingRewardAttempt.class);
        ReflectionTestUtils.setField(attempt, "publicId", attemptId);
        ReflectionTestUtils.setField(attempt, "rewardKeycap", mainKeycap);
        ReflectionTestUtils.setField(attempt, "bonusRewardKeycap", bonusKeycap);
        ReflectionTestUtils.setField(attempt, "rewardPointAmount", 2);
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
