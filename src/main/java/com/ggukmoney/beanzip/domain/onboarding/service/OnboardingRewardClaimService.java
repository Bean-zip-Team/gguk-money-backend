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
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OnboardingRewardClaimService {

    private static final int REQUIRED_ACCEPTED_TAP_COUNT = 45;
    private static final String POINT_REASON_ONBOARDING_REWARD = "ONBOARDING_REWARD";

    private final OnboardingRewardAttemptRepository attemptRepository;
    private final PointAccountService pointAccountService;
    private final PointLedgerService pointLedgerService;
    private final UserKeycapRepository userKeycapRepository;
    private final Clock clock;

    @Transactional(propagation = Propagation.MANDATORY)
    public boolean claimForNewUser(AppUser user, UUID onboardingAttemptId) {
        OnboardingRewardAttempt attempt = attemptRepository.findByPublicIdWithRewardKeycapForUpdate(onboardingAttemptId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "ONBOARDING_ATTEMPT_NOT_FOUND"));
        Instant now = Instant.now(clock);
        validateClaimable(attempt, now);

        PointAccount account = pointAccountService.credit(user.getId(), attempt.getRewardPointAmount());
        pointLedgerService.recordCredit(
                account,
                user,
                attempt.getRewardPointAmount(),
                POINT_REASON_ONBOARDING_REWARD,
                attempt.getPublicId()
        );

        Keycap rewardKeycap = attempt.getRewardKeycap();
        userKeycapRepository.findByUserIdAndKeycapIdForUpdate(user.getId(), rewardKeycap.getId())
                .orElseGet(() -> userKeycapRepository.save(
                        UserKeycap.createCompletedOnboardingReward(user, rewardKeycap, now)
                ));
        user.claimOnboardingReward(now);
        attempt.claim(user, now);
        return true;
    }

    @Transactional(readOnly = true)
    public boolean isClaimedByUser(UUID userId, UUID onboardingAttemptId) {
        if (onboardingAttemptId == null) {
            return false;
        }
        return attemptRepository.findByPublicId(onboardingAttemptId)
                .filter(attempt -> attempt.getStatus() == OnboardingRewardAttempt.Status.CLAIMED)
                .map(OnboardingRewardAttempt::getClaimedUser)
                .filter(claimedUser -> claimedUser.getId().equals(userId))
                .isPresent();
    }

    private void validateClaimable(OnboardingRewardAttempt attempt, Instant now) {
        if (attempt.getStatus() != OnboardingRewardAttempt.Status.OPENED) {
            if (attempt.getClaimedUser() != null || attempt.getClaimedAt() != null) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "ONBOARDING_ATTEMPT_ALREADY_CLAIMED");
            }
            throw new ResponseStatusException(HttpStatus.CONFLICT, "ONBOARDING_ATTEMPT_NOT_OPENED");
        }
        if (attempt.getClaimedUser() != null || attempt.getClaimedAt() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "ONBOARDING_ATTEMPT_ALREADY_CLAIMED");
        }
        if (!attempt.getExpiresAt().isAfter(now)) {
            throw new ResponseStatusException(HttpStatus.GONE, "ONBOARDING_ATTEMPT_EXPIRED");
        }
        Keycap rewardKeycap = attempt.getRewardKeycap();
        if (attempt.getAcceptedTapCount() == null
                || attempt.getAcceptedTapCount() != REQUIRED_ACCEPTED_TAP_COUNT
                || rewardKeycap == null
                || !rewardKeycap.isActive()
                || rewardKeycap.getRequiredShardCount() == null
                || rewardKeycap.getRequiredShardCount() < 0
                || attempt.getRewardPointAmount() == null
                || attempt.getRewardPointAmount() < 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "ONBOARDING_REWARD_INVALID");
        }
    }
}
