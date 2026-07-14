package com.ggukmoney.beanzip.domain.onboarding.service;

import com.ggukmoney.beanzip.domain.keycap.entity.Keycap;
import com.ggukmoney.beanzip.domain.keycap.repository.KeycapRepository;
import com.ggukmoney.beanzip.domain.onboarding.dto.mapper.OnboardingRewardAttemptMapper;
import com.ggukmoney.beanzip.domain.onboarding.dto.request.OnboardingKeycapBoxOpenRequest;
import com.ggukmoney.beanzip.domain.onboarding.dto.response.OnboardingKeycapBoxOpenResponse;
import com.ggukmoney.beanzip.domain.onboarding.entity.OnboardingRewardAttempt;
import com.ggukmoney.beanzip.domain.onboarding.repository.OnboardingRewardAttemptRepository;
import com.ggukmoney.beanzip.global.config.OnboardingRewardConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class OnboardingKeycapBoxOpenService {

    private final OnboardingRewardAttemptRepository attemptRepository;
    private final KeycapRepository keycapRepository;
    private final OnboardingTapValidator tapValidator;
    private final OnboardingKeycapBoxOpenRequestHasher requestHasher;
    private final OnboardingRewardConfig rewardConfig;
    private final OnboardingRewardAttemptMapper mapper;
    private final PlatformTransactionManager transactionManager;
    private final Clock clock;

    public OnboardingKeycapBoxOpenResponse open(OnboardingKeycapBoxOpenRequest request) {
        int acceptedTapCount = tapValidator.validateCompleted(request);
        String requestHash = requestHasher.hash(request);

        Optional<OnboardingKeycapBoxOpenResponse> replay = findReplay(request, requestHash);
        if (replay.isPresent()) {
            return replay.get();
        }

        try {
            TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
            return transactionTemplate.execute(status -> openInTransaction(request, requestHash, acceptedTapCount));
        } catch (DataIntegrityViolationException exception) {
            return findReplay(request, requestHash).orElseThrow(() -> exception);
        }
    }

    private OnboardingKeycapBoxOpenResponse openInTransaction(
            OnboardingKeycapBoxOpenRequest request,
            String requestHash,
            int acceptedTapCount
    ) {
        Optional<OnboardingKeycapBoxOpenResponse> replay = findReplay(request, requestHash);
        if (replay.isPresent()) {
            return replay.get();
        }

        OnboardingRewardConfig.OnboardingRewardPolicy policy = rewardConfig.resolve();
        Keycap rewardKeycap = keycapRepository.findByCode(policy.rewardKeycapCode())
                .filter(Keycap::isActive)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "ONBOARDING_REWARD_NOT_AVAILABLE"));

        Instant openedAt = Instant.now(clock);
        OnboardingRewardAttempt attempt = OnboardingRewardAttempt.open(
                request.tapSessionId(),
                requestHash,
                acceptedTapCount,
                rewardKeycap,
                policy.rewardPointAmount(),
                openedAt,
                openedAt.plus(policy.attemptTtl())
        );
        return mapper.mapToResponse(attemptRepository.save(attempt));
    }

    private Optional<OnboardingKeycapBoxOpenResponse> findReplay(
            OnboardingKeycapBoxOpenRequest request,
            String requestHash
    ) {
        return attemptRepository.findByTapSessionIdWithRewardKeycap(request.tapSessionId())
                .map(existing -> {
                    if (!existing.getRequestHash().equals(requestHash)) {
                        throw new ResponseStatusException(HttpStatus.CONFLICT, "ONBOARDING_TAP_SESSION_REUSED");
                    }
                    if (!existing.getExpiresAt().isAfter(Instant.now(clock))) {
                        throw new ResponseStatusException(HttpStatus.GONE, "ONBOARDING_ATTEMPT_EXPIRED");
                    }
                    return mapper.mapToResponse(existing);
                });
    }
}
