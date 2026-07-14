package com.ggukmoney.beanzip.domain.onboarding.service;

import com.ggukmoney.beanzip.domain.keycap.entity.Keycap;
import com.ggukmoney.beanzip.domain.keycap.repository.KeycapRepository;
import com.ggukmoney.beanzip.domain.onboarding.dto.mapper.OnboardingRewardAttemptMapper;
import com.ggukmoney.beanzip.domain.onboarding.dto.request.OnboardingKeycapBoxOpenRequest;
import com.ggukmoney.beanzip.domain.onboarding.dto.response.OnboardingKeycapBoxOpenResponse;
import com.ggukmoney.beanzip.domain.onboarding.entity.OnboardingRewardAttempt;
import com.ggukmoney.beanzip.domain.onboarding.repository.OnboardingRewardAttemptRepository;
import com.ggukmoney.beanzip.global.config.OnboardingRewardConfig;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.web.server.ResponseStatusException;

import java.lang.reflect.Constructor;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OnboardingKeycapBoxOpenServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-15T01:00:05Z");

    private final OnboardingRewardAttemptRepository attemptRepository = mock(OnboardingRewardAttemptRepository.class);
    private final KeycapRepository keycapRepository = mock(KeycapRepository.class);
    private final OnboardingTapValidator tapValidator = new OnboardingTapValidator();
    private final OnboardingKeycapBoxOpenRequestHasher requestHasher = new OnboardingKeycapBoxOpenRequestHasher();
    private final OnboardingRewardConfig rewardConfig = mock(OnboardingRewardConfig.class);
    private final OnboardingRewardAttemptMapper mapper = mock(OnboardingRewardAttemptMapper.class);
    private final OnboardingKeycapBoxOpenService service = new OnboardingKeycapBoxOpenService(
            attemptRepository,
            keycapRepository,
            tapValidator,
            requestHasher,
            rewardConfig,
            mapper,
            new NoOpTransactionManager(),
            Clock.fixed(NOW, ZoneOffset.UTC)
    );

    @Test
    void opensOnboardingBoxAndPersistsOpenedAttemptSnapshot() {
        UUID tapSessionId = UUID.randomUUID();
        OnboardingKeycapBoxOpenRequest request = request(tapSessionId);
        Keycap keycap = keycap("ONBOARDING_BASIC", true);
        OnboardingKeycapBoxOpenResponse mapped = response(false);
        when(attemptRepository.findByTapSessionIdWithRewardKeycap(tapSessionId)).thenReturn(Optional.empty());
        when(rewardConfig.resolve()).thenReturn(new OnboardingRewardConfig.OnboardingRewardPolicy(
                "ONBOARDING_BASIC",
                100,
                Duration.ofHours(24)
        ));
        when(keycapRepository.findByCode("ONBOARDING_BASIC")).thenReturn(Optional.of(keycap));
        when(attemptRepository.save(any(OnboardingRewardAttempt.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(mapper.mapToResponse(any(OnboardingRewardAttempt.class))).thenReturn(mapped);

        OnboardingKeycapBoxOpenResponse response = service.open(request);

        assertThat(response).isEqualTo(mapped);
        var captor = org.mockito.ArgumentCaptor.forClass(OnboardingRewardAttempt.class);
        verify(attemptRepository).save(captor.capture());
        OnboardingRewardAttempt attempt = captor.getValue();
        assertThat(attempt.getTapSessionId()).isEqualTo(tapSessionId);
        assertThat(attempt.getAcceptedTapCount()).isEqualTo(45);
        assertThat(attempt.getRewardKeycap()).isEqualTo(keycap);
        assertThat(attempt.getRewardPointAmount()).isEqualTo(100);
        assertThat(attempt.getStatus()).isEqualTo(OnboardingRewardAttempt.Status.OPENED);
        assertThat(attempt.getOpenedAt()).isEqualTo(NOW);
        assertThat(attempt.getExpiresAt()).isEqualTo(NOW.plus(Duration.ofHours(24)));
        assertThat(attempt.getClaimedUser()).isNull();
        assertThat(attempt.getClaimedAt()).isNull();
    }

    @Test
    void replaysExistingUnexpiredAttemptForSameTapSessionAndRequestHash() {
        UUID tapSessionId = UUID.randomUUID();
        OnboardingKeycapBoxOpenRequest request = request(tapSessionId);
        OnboardingRewardAttempt existing = existingAttempt(tapSessionId, requestHasher.hash(request), NOW.plusSeconds(60));
        OnboardingKeycapBoxOpenResponse mapped = response(false);
        when(attemptRepository.findByTapSessionIdWithRewardKeycap(tapSessionId)).thenReturn(Optional.of(existing));
        when(mapper.mapToResponse(existing)).thenReturn(mapped);

        OnboardingKeycapBoxOpenResponse response = service.open(request);

        assertThat(response).isEqualTo(mapped);
        verify(attemptRepository, never()).save(any());
        verify(keycapRepository, never()).findByCode(any());
    }

    @Test
    void rejectsSameTapSessionWithDifferentRequestHash() {
        UUID tapSessionId = UUID.randomUUID();
        when(attemptRepository.findByTapSessionIdWithRewardKeycap(tapSessionId))
                .thenReturn(Optional.of(existingAttempt(tapSessionId, "different-hash", NOW.plusSeconds(60))));

        assertThatThrownBy(() -> service.open(request(tapSessionId)))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(exception -> ((ResponseStatusException) exception).getReason())
                .isEqualTo("ONBOARDING_TAP_SESSION_REUSED");
    }

    @Test
    void rejectsExpiredReplayForSameTapSession() {
        UUID tapSessionId = UUID.randomUUID();
        OnboardingKeycapBoxOpenRequest request = request(tapSessionId);
        when(attemptRepository.findByTapSessionIdWithRewardKeycap(tapSessionId))
                .thenReturn(Optional.of(existingAttempt(tapSessionId, requestHasher.hash(request), NOW.minusSeconds(1))));

        assertThatThrownBy(() -> service.open(request))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(exception -> ((ResponseStatusException) exception).getReason())
                .isEqualTo("ONBOARDING_ATTEMPT_EXPIRED");
    }

    @Test
    void rejectsInvalidRewardConfigAndInactiveKeycap() {
        UUID tapSessionId = UUID.randomUUID();
        OnboardingKeycapBoxOpenRequest request = request(tapSessionId);
        when(attemptRepository.findByTapSessionIdWithRewardKeycap(tapSessionId)).thenReturn(Optional.empty());
        when(rewardConfig.resolve()).thenReturn(new OnboardingRewardConfig.OnboardingRewardPolicy(
                "ONBOARDING_BASIC",
                100,
                Duration.ofHours(24)
        ));
        when(keycapRepository.findByCode("ONBOARDING_BASIC")).thenReturn(Optional.of(keycap("ONBOARDING_BASIC", false)));

        assertThatThrownBy(() -> service.open(request))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(exception -> ((ResponseStatusException) exception).getReason())
                .isEqualTo("ONBOARDING_REWARD_NOT_AVAILABLE");

        verify(attemptRepository, never()).save(any());
    }

    private static OnboardingKeycapBoxOpenRequest request(UUID tapSessionId) {
        List<OnboardingKeycapBoxOpenRequest.TapEvent> events = new ArrayList<>();
        for (int sequence = 1; sequence <= 45; sequence++) {
            events.add(new OnboardingKeycapBoxOpenRequest.TapEvent(
                    sequence,
                    Instant.parse("2026-07-15T01:00:00Z").plusMillis(sequence)
            ));
        }
        return new OnboardingKeycapBoxOpenRequest(tapSessionId, events);
    }

    private static OnboardingKeycapBoxOpenResponse response(boolean completed) {
        return new OnboardingKeycapBoxOpenResponse(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "ONBOARDING_BASIC",
                "온보딩 키캡",
                "COMMON",
                "https://example.com/keycaps/onboarding-basic.png",
                "https://example.com/keycaps/onboarding-basic.mp3",
                completed,
                100,
                NOW,
                NOW.plus(Duration.ofHours(24))
        );
    }

    private static OnboardingRewardAttempt existingAttempt(UUID tapSessionId, String requestHash, Instant expiresAt) {
        OnboardingRewardAttempt attempt = newInstance(OnboardingRewardAttempt.class);
        ReflectionTestUtils.setField(attempt, "tapSessionId", tapSessionId);
        ReflectionTestUtils.setField(attempt, "requestHash", requestHash);
        ReflectionTestUtils.setField(attempt, "expiresAt", expiresAt);
        return attempt;
    }

    private static Keycap keycap(String code, boolean active) {
        Keycap keycap = newInstance(Keycap.class);
        ReflectionTestUtils.setField(keycap, "code", code);
        ReflectionTestUtils.setField(keycap, "name", "온보딩 키캡");
        ReflectionTestUtils.setField(keycap, "grade", Keycap.Grade.COMMON);
        ReflectionTestUtils.setField(keycap, "imageUrl", "https://example.com/keycaps/onboarding-basic.png");
        ReflectionTestUtils.setField(keycap, "soundUrl", "https://example.com/keycaps/onboarding-basic.mp3");
        ReflectionTestUtils.setField(keycap, "active", active);
        return keycap;
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

    private static class NoOpTransactionManager implements PlatformTransactionManager {
        @Override
        public TransactionStatus getTransaction(TransactionDefinition definition) {
            return new SimpleTransactionStatus();
        }

        @Override
        public void commit(TransactionStatus status) {
        }

        @Override
        public void rollback(TransactionStatus status) {
        }
    }
}
