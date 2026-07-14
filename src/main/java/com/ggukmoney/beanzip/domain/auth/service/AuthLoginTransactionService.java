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
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthLoginTransactionService {

    private final AuthIdentityRepository authIdentityRepository;
    private final UserService userService;
    private final PointAccountService pointAccountService;
    private final KeycapBoxAccountService keycapBoxAccountService;
    private final UserTapProgressService userTapProgressService;
    private final TapPolicyConfig tapPolicyConfig;
    private final OnboardingRewardClaimService onboardingRewardClaimService;

    @Transactional
    public LoginTransactionResult loginWithTossUser(
            String providerUserId,
            String nickname,
            String profileImageUrl,
            UUID onboardingAttemptId
    ) {
        AuthIdentity identity = authIdentityRepository
                .findByProviderAndProviderUserId(AuthIdentity.Provider.TOSS, providerUserId)
                .orElse(null);

        if (identity == null) {
            AppUser user = userService.createActive(nickname, profileImageUrl);
            authIdentityRepository.save(AuthIdentity.toss(user, providerUserId));
            pointAccountService.createFor(user);
            keycapBoxAccountService.createFor(user);
            userTapProgressService.createFor(user, tapPolicyConfig);
            boolean onboardingRewardApplied = onboardingAttemptId != null
                    && onboardingRewardClaimService.claimForNewUser(user, onboardingAttemptId);
            return new LoginTransactionResult(user.getId(), true, onboardingRewardApplied);
        }

        AppUser user = identity.getUser();
        if (user.isWithdrawn()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "ACCOUNT_WITHDRAWN");
        }
        AppUser loggedInUser = userService.recordLogin(user, nickname, profileImageUrl);
        boolean onboardingRewardApplied = onboardingRewardClaimService.isClaimedByUser(
                loggedInUser.getId(),
                onboardingAttemptId
        );
        return new LoginTransactionResult(loggedInUser.getId(), false, onboardingRewardApplied);
    }

    public record LoginTransactionResult(
            UUID userId,
            boolean newUser,
            boolean onboardingRewardApplied
    ) {
    }
}
