package com.ggukmoney.beanzip.domain.auth.service;

import com.ggukmoney.beanzip.domain.auth.entity.AuthIdentity;
import com.ggukmoney.beanzip.domain.auth.repository.AuthIdentityRepository;
import com.ggukmoney.beanzip.domain.keycap.service.KeycapBoxAccountService;
import com.ggukmoney.beanzip.domain.onboarding.service.OnboardingRewardClaimService;
import com.ggukmoney.beanzip.domain.point.service.PointAccountService;
import com.ggukmoney.beanzip.domain.tap.service.UserTapProgressService;
import com.ggukmoney.beanzip.domain.user.entity.AppUser;
import com.ggukmoney.beanzip.domain.user.service.UserService;
import com.ggukmoney.beanzip.global.config.TapPolicyConfig;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthLoginTransactionServiceTest {

    private final AuthIdentityRepository authIdentityRepository = mock(AuthIdentityRepository.class);
    private final UserService userService = mock(UserService.class);
    private final PointAccountService pointAccountService = mock(PointAccountService.class);
    private final KeycapBoxAccountService keycapBoxAccountService = mock(KeycapBoxAccountService.class);
    private final UserTapProgressService userTapProgressService = mock(UserTapProgressService.class);
    private final TapPolicyConfig tapPolicyConfig = mock(TapPolicyConfig.class);
    private final OnboardingRewardClaimService onboardingRewardClaimService = mock(OnboardingRewardClaimService.class);
    private final AuthLoginTransactionService service = new AuthLoginTransactionService(
            authIdentityRepository,
            userService,
            pointAccountService,
            keycapBoxAccountService,
            userTapProgressService,
            tapPolicyConfig,
            onboardingRewardClaimService
    );

    @Test
    void newUserWithOnboardingAttemptClaimsRewardInLoginTransaction() {
        UUID userId = UUID.randomUUID();
        UUID attemptId = UUID.randomUUID();
        AppUser user = withId(AppUser.createActive("Bean", "https://img"), userId);
        when(authIdentityRepository.findByProviderAndProviderUserId(AuthIdentity.Provider.TOSS, "toss-user"))
                .thenReturn(Optional.empty());
        when(userService.createActive("Bean", "https://img")).thenReturn(user);
        when(onboardingRewardClaimService.claimForNewUser(user, attemptId)).thenReturn(true);

        AuthLoginTransactionService.LoginTransactionResult result = service.loginWithTossUser(
                "toss-user",
                "Bean",
                "https://img",
                attemptId
        );

        assertThat(result.userId()).isEqualTo(userId);
        assertThat(result.newUser()).isTrue();
        assertThat(result.onboardingRewardApplied()).isTrue();
        verify(authIdentityRepository).save(any(AuthIdentity.class));
        verify(pointAccountService).createFor(user);
        verify(keycapBoxAccountService).createFor(user);
        verify(userTapProgressService).createFor(user, tapPolicyConfig);
        verify(onboardingRewardClaimService).claimForNewUser(user, attemptId);
    }

    @Test
    void existingUserWithInvalidOnboardingAttemptStillLogsInWithoutClaimingReward() {
        UUID userId = UUID.randomUUID();
        UUID attemptId = UUID.randomUUID();
        AppUser user = withId(AppUser.createActive("Old", null), userId);
        when(authIdentityRepository.findByProviderAndProviderUserId(AuthIdentity.Provider.TOSS, "toss-user"))
                .thenReturn(Optional.of(AuthIdentity.toss(user, "toss-user")));
        when(userService.recordLogin(user, "Bean", "https://img")).thenReturn(user);
        when(onboardingRewardClaimService.isClaimedByUser(userId, attemptId)).thenReturn(false);

        AuthLoginTransactionService.LoginTransactionResult result = service.loginWithTossUser(
                "toss-user",
                "Bean",
                "https://img",
                attemptId
        );

        assertThat(result.userId()).isEqualTo(userId);
        assertThat(result.newUser()).isFalse();
        assertThat(result.onboardingRewardApplied()).isFalse();
        verify(onboardingRewardClaimService, never()).claimForNewUser(any(), any());
        verify(onboardingRewardClaimService).isClaimedByUser(userId, attemptId);
        verify(pointAccountService, never()).createFor(any());
    }

    @Test
    void existingUserWithOwnClaimedAttemptReturnsAppliedWithoutClaimingAgain() {
        UUID userId = UUID.randomUUID();
        UUID attemptId = UUID.randomUUID();
        AppUser user = withId(AppUser.createActive("Old", null), userId);
        when(authIdentityRepository.findByProviderAndProviderUserId(AuthIdentity.Provider.TOSS, "toss-user"))
                .thenReturn(Optional.of(AuthIdentity.toss(user, "toss-user")));
        when(userService.recordLogin(user, "Bean", "https://img")).thenReturn(user);
        when(onboardingRewardClaimService.isClaimedByUser(userId, attemptId)).thenReturn(true);

        AuthLoginTransactionService.LoginTransactionResult result = service.loginWithTossUser(
                "toss-user",
                "Bean",
                "https://img",
                attemptId
        );

        assertThat(result.newUser()).isFalse();
        assertThat(result.onboardingRewardApplied()).isTrue();
        verify(onboardingRewardClaimService, never()).claimForNewUser(any(), any());
        verify(onboardingRewardClaimService).isClaimedByUser(userId, attemptId);
    }

    @Test
    void existingUserWithAttemptClaimedByAnotherUserStillLogsInWithoutAppliedReward() {
        UUID userId = UUID.randomUUID();
        UUID attemptId = UUID.randomUUID();
        AppUser user = withId(AppUser.createActive("Old", null), userId);
        when(authIdentityRepository.findByProviderAndProviderUserId(AuthIdentity.Provider.TOSS, "toss-user"))
                .thenReturn(Optional.of(AuthIdentity.toss(user, "toss-user")));
        when(userService.recordLogin(user, "Bean", "https://img")).thenReturn(user);
        when(onboardingRewardClaimService.isClaimedByUser(userId, attemptId)).thenReturn(false);

        AuthLoginTransactionService.LoginTransactionResult result = service.loginWithTossUser(
                "toss-user",
                "Bean",
                "https://img",
                attemptId
        );

        assertThat(result.newUser()).isFalse();
        assertThat(result.onboardingRewardApplied()).isFalse();
        verify(onboardingRewardClaimService, never()).claimForNewUser(any(), any());
        verify(onboardingRewardClaimService).isClaimedByUser(userId, attemptId);
    }

    private static AppUser withId(AppUser user, UUID userId) {
        ReflectionTestUtils.setField(user, "id", userId);
        return user;
    }
}
