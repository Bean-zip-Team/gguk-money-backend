package com.ggukmoney.beanzip.domain.onboarding.service;

import com.ggukmoney.beanzip.domain.keycap.entity.Keycap;
import com.ggukmoney.beanzip.domain.keycap.entity.UserKeycap;
import com.ggukmoney.beanzip.domain.keycap.repository.UserKeycapRepository;
import com.ggukmoney.beanzip.domain.onboarding.entity.OnboardingRewardAttempt;
import com.ggukmoney.beanzip.domain.onboarding.repository.OnboardingRewardAttemptRepository;
import com.ggukmoney.beanzip.domain.point.entity.PointAccount;
import com.ggukmoney.beanzip.domain.point.service.PointAccountService;
import com.ggukmoney.beanzip.domain.point.service.PointLedgerService;
import com.ggukmoney.beanzip.domain.user.entity.AppUser;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.lang.reflect.Constructor;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OnboardingRewardClaimServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-15T03:00:00Z");

    private final OnboardingRewardAttemptRepository attemptRepository = mock(OnboardingRewardAttemptRepository.class);
    private final PointAccountService pointAccountService = mock(PointAccountService.class);
    private final PointLedgerService pointLedgerService = mock(PointLedgerService.class);
    private final UserKeycapRepository userKeycapRepository = mock(UserKeycapRepository.class);
    private final OnboardingRewardClaimService service = new OnboardingRewardClaimService(
            attemptRepository,
            pointAccountService,
            pointLedgerService,
            userKeycapRepository,
            Clock.fixed(NOW, ZoneOffset.UTC)
    );

    @Test
    void claimsOpenedAttemptAndGrantsPointAndBothCompletedKeycaps() {
        UUID userId = UUID.randomUUID();
        UUID attemptId = UUID.randomUUID();
        AppUser user = withId(AppUser.createActive("Bean", null), userId);
        Keycap mainKeycap = keycap(17L, 3, true);
        Keycap bonusKeycap = keycap(18L, 3, true);
        OnboardingRewardAttempt attempt = attempt(attemptId, mainKeycap, bonusKeycap, 2, NOW.plusSeconds(60));
        PointAccount account = PointAccount.createFor(user);
        when(attemptRepository.findByPublicIdWithRewardKeycapForUpdate(attemptId)).thenReturn(Optional.of(attempt));
        when(pointAccountService.credit(userId, 2)).thenReturn(account);
        when(userKeycapRepository.findByUserIdAndKeycapIdForUpdate(userId, 17L)).thenReturn(Optional.empty());
        when(userKeycapRepository.findByUserIdAndKeycapIdForUpdate(userId, 18L)).thenReturn(Optional.empty());

        boolean applied = service.claimForNewUser(user, attemptId);

        assertThat(applied).isTrue();
        assertThat(user.isOnboardingRewardClaimed()).isTrue();
        assertThat(user.getOnboardingCompletedAt()).isEqualTo(NOW);
        assertThat(attempt.getStatus()).isEqualTo(OnboardingRewardAttempt.Status.CLAIMED);
        assertThat(attempt.getClaimedUser()).isEqualTo(user);
        assertThat(attempt.getClaimedAt()).isEqualTo(NOW);
        verify(pointLedgerService).recordCredit(account, user, 2, "ONBOARDING_REWARD", attemptId);
        verify(userKeycapRepository, org.mockito.Mockito.times(2)).save(any(UserKeycap.class));
    }

    @Test
    void rejectsMissingAttemptWithoutGrantingReward() {
        UUID attemptId = UUID.randomUUID();
        AppUser user = withId(AppUser.createActive("Bean", null), UUID.randomUUID());
        when(attemptRepository.findByPublicIdWithRewardKeycapForUpdate(attemptId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.claimForNewUser(user, attemptId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(exception -> {
                    ResponseStatusException response = (ResponseStatusException) exception;
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(response.getReason()).isEqualTo("ONBOARDING_ATTEMPT_NOT_FOUND");
                });

        verify(pointAccountService, never()).credit(any(), anyLong());
        verify(pointLedgerService, never()).recordCredit(any(), any(), anyLong(), any(), any());
        verify(userKeycapRepository, never()).save(any());
    }

    @Test
    void reportsClaimedBySameUserOnlyForExistingUserReplay() {
        UUID userId = UUID.randomUUID();
        UUID attemptId = UUID.randomUUID();
        AppUser user = withId(AppUser.createActive("Bean", null), userId);
        OnboardingRewardAttempt attempt = attempt(
                attemptId, keycap(17L, 3, true), keycap(18L, 3, true), 2, NOW.plusSeconds(60)
        );
        claimAttempt(attempt, user, NOW);
        when(attemptRepository.findByPublicId(attemptId)).thenReturn(Optional.of(attempt));

        assertThat(service.isClaimedByUser(userId, attemptId)).isTrue();
        assertThat(service.isClaimedByUser(UUID.randomUUID(), attemptId)).isFalse();
    }

    private static OnboardingRewardAttempt attempt(
            UUID publicId,
            Keycap keycap,
            Keycap bonusKeycap,
            int pointAmount,
            Instant expiresAt
    ) {
        OnboardingRewardAttempt attempt = OnboardingRewardAttempt.open(
                UUID.randomUUID(),
                "hash",
                45,
                keycap,
                bonusKeycap,
                pointAmount,
                NOW,
                expiresAt
        );
        ReflectionTestUtils.setField(attempt, "publicId", publicId);
        return attempt;
    }

    private static void claimAttempt(OnboardingRewardAttempt attempt, AppUser user, Instant claimedAt) {
        ReflectionTestUtils.setField(attempt, "status", OnboardingRewardAttempt.Status.CLAIMED);
        ReflectionTestUtils.setField(attempt, "claimedUser", user);
        ReflectionTestUtils.setField(attempt, "claimedAt", claimedAt);
    }

    private static Keycap keycap(Long id, int requiredShardCount, boolean active) {
        Keycap keycap = newInstance(Keycap.class);
        ReflectionTestUtils.setField(keycap, "id", id);
        ReflectionTestUtils.setField(keycap, "publicId", UUID.randomUUID());
        ReflectionTestUtils.setField(keycap, "code", "ONBOARDING_BASIC");
        ReflectionTestUtils.setField(keycap, "name", "Onboarding Basic");
        ReflectionTestUtils.setField(keycap, "grade", Keycap.Grade.COMMON);
        ReflectionTestUtils.setField(keycap, "requiredShardCount", requiredShardCount);
        ReflectionTestUtils.setField(keycap, "active", active);
        return keycap;
    }

    private static AppUser withId(AppUser user, UUID userId) {
        ReflectionTestUtils.setField(user, "id", userId);
        return user;
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
