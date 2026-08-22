package com.ggukmoney.beanzip.domain.auth.service;

import com.ggukmoney.beanzip.domain.auth.entity.AuthIdentity;
import com.ggukmoney.beanzip.domain.auth.repository.AuthIdentityRepository;
import com.ggukmoney.beanzip.domain.keycap.service.KeycapBoxAccountService;
import com.ggukmoney.beanzip.domain.onboarding.service.OnboardingRewardClaimService;
import com.ggukmoney.beanzip.domain.point.service.PointAccountService;
import com.ggukmoney.beanzip.domain.tap.entity.UserTapDaily;
import com.ggukmoney.beanzip.domain.tap.service.UserTapDailyService;
import com.ggukmoney.beanzip.domain.tap.service.UserTapProgressService;
import com.ggukmoney.beanzip.domain.tap.service.UserTapSessionService;
import com.ggukmoney.beanzip.domain.user.entity.AppUser;
import com.ggukmoney.beanzip.domain.user.service.UserService;
import com.ggukmoney.beanzip.global.config.TapPolicyConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthLoginTransactionService {

    private static final int ONBOARDING_TAP_COUNT = 45;

    private final AuthIdentityRepository authIdentityRepository;
    private final UserService userService;
    private final PointAccountService pointAccountService;
    private final KeycapBoxAccountService keycapBoxAccountService;
    private final UserTapProgressService userTapProgressService;
    private final UserTapSessionService userTapSessionService;
    private final UserTapDailyService userTapDailyService;
    private final TapPolicyConfig tapPolicyConfig;
    private final OnboardingRewardClaimService onboardingRewardClaimService;
    private final TossLoginConsentHistoryService tossLoginConsentHistoryService;
    private final Clock clock;
    private final ZoneId businessZoneId;

    @Transactional
    public LoginTransactionResult loginWithTossUser(
            String providerUserId,
            String nickname,
            String profileImageUrl,
            UUID onboardingAttemptId
    ) {
        return loginWithTossUser(providerUserId, nickname, profileImageUrl, onboardingAttemptId, List.of());
    }

    @Transactional
    public LoginTransactionResult loginWithTossUser(
            String providerUserId,
            String nickname,
            String profileImageUrl,
            UUID onboardingAttemptId,
            List<String> agreedTerms
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
            userTapSessionService.createFor(user, clock.instant(), tapPolicyConfig);
            boolean onboardingRewardApplied = onboardingAttemptId != null
                    && onboardingRewardClaimService.claimForNewUser(user, onboardingAttemptId);
            if (onboardingRewardApplied) {
                seedTodayTapCountFromOnboarding(user);
            }
            tossLoginConsentHistoryService.recordAgreements(user.getId(), agreedTerms, "LOGIN");
            return new LoginTransactionResult(user.getId(), true, onboardingRewardApplied);
        }

        AppUser user = identity.getUser();
        if (user.isWithdrawn()) {
            AppUser reactivatedUser = userService.reactivate(user, nickname, profileImageUrl);
            tossLoginConsentHistoryService.recordAgreements(reactivatedUser.getId(), agreedTerms, "LOGIN");
            return new LoginTransactionResult(reactivatedUser.getId(), false, false);
        }
        AppUser loggedInUser = userService.recordLogin(user, nickname, profileImageUrl);
        boolean onboardingRewardApplied = onboardingRewardClaimService.isClaimedByUser(
                loggedInUser.getId(),
                onboardingAttemptId
        );
        tossLoginConsentHistoryService.recordAgreements(loggedInUser.getId(), agreedTerms, "LOGIN");
        return new LoginTransactionResult(loggedInUser.getId(), false, onboardingRewardApplied);
    }

    private void seedTodayTapCountFromOnboarding(AppUser user) {
        LocalDate today = LocalDate.ofInstant(clock.instant(), businessZoneId);
        UserTapDaily daily = userTapDailyService.getOrCreate(user, today);
        daily.addValidTaps(ONBOARDING_TAP_COUNT);
        daily.addTotalValidTaps(ONBOARDING_TAP_COUNT);
        userTapDailyService.save(daily);
    }

    public record LoginTransactionResult(
            UUID userId,
            boolean newUser,
            boolean onboardingRewardApplied
    ) {
    }
}
