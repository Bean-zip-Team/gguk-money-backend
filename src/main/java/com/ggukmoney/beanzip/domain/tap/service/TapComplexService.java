package com.ggukmoney.beanzip.domain.tap.service;

import com.ggukmoney.beanzip.domain.point.entity.PointAccount;
import com.ggukmoney.beanzip.domain.point.service.PointAccountService;
import com.ggukmoney.beanzip.domain.point.service.PointLedgerService;
import com.ggukmoney.beanzip.domain.tap.config.TapPolicyConfig;
import com.ggukmoney.beanzip.domain.tap.entity.TapBatch;
import com.ggukmoney.beanzip.domain.tap.entity.UserTapDaily;
import com.ggukmoney.beanzip.domain.tap.infra.TapRateLimiter;
import com.ggukmoney.beanzip.domain.user.entity.AppUser;
import com.ggukmoney.beanzip.domain.user.repository.AppUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Facade orchestrating the single-repository tap/point services. Owns no repository directly.
 * This is the only entry point {@code TapController} calls.
 */
@Service
@RequiredArgsConstructor
public class TapComplexService {

    private static final String CREDIT_REASON_TAP = "TAP_REWARD";

    private final TapBatchService tapBatchService;
    private final UserTapDailyService userTapDailyService;
    private final PointAccountService pointAccountService;
    private final PointLedgerService pointLedgerService;
    private final TapRateLimiter tapRateLimiter;
    private final TapPolicyConfig tapPolicyConfig;
    private final TapCurveCalculator tapCurveCalculator;
    private final TapValidityCalculator tapValidityCalculator;
    private final TapBotDetector tapBotDetector;
    private final AppUserRepository appUserRepository;

    @Transactional
    public TapSubmitOutcome submitBatch(TapSubmitCommand command) {
        UUID userId = command.userId();
        if (!tapRateLimiter.tryConsume(userId, tapPolicyConfig.rateLimitCapacity(), tapPolicyConfig.rateLimitRefillPerSecond())) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "TAP_RATE_LIMITED");
        }

        Optional<TapBatch> existing = tapBatchService.findExisting(userId, command.tapSessionId(), command.sequence());
        if (existing.isPresent()) {
            long balance = pointAccountService.getBalance(userId);
            return new TapSubmitOutcome(existing.get().getAcceptedCount(), 0, balance);
        }

        AppUser user = appUserRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "AUTH_USER_NOT_FOUND"));

        Instant now = Instant.now();
        List<TapBatch> recentBatches = tapBatchService.findRecentForBotCheck(userId, tapPolicyConfig.botSampleSize());
        Duration elapsedSinceLastBatch = recentBatches.isEmpty()
                ? null
                : Duration.between(recentBatches.get(0).getCreatedAt(), now);

        int minuteRemaining = tapPolicyConfig.maxPerMinute() - tapRateLimiter.getMinuteCount(userId);

        UserTapDaily daily = userTapDailyService.getOrCreateToday(user, tapCurveCalculator, tapPolicyConfig);
        int dailyRemaining = tapPolicyConfig.maxPerDay() - daily.getValidTapCount();

        int acceptedCount = tapValidityCalculator.calculateAcceptedCount(
                command.submittedCount(), elapsedSinceLastBatch, minuteRemaining, dailyRemaining, tapPolicyConfig
        );

        List<Instant> botCheckTimestamps = new ArrayList<>();
        botCheckTimestamps.add(now);
        recentBatches.forEach(batch -> botCheckTimestamps.add(batch.getCreatedAt()));
        boolean botSuspected = tapBotDetector.isSuspicious(botCheckTimestamps, tapPolicyConfig);

        TapBatch batch = TapBatch.createFor(user, command.tapSessionId(), command.sequence(), command.submittedCount(), requestHash(command));
        batch.markAccepted(acceptedCount);
        if (botSuspected) {
            batch.markBotSuspected();
        }
        batch = tapBatchService.save(batch);

        tapRateLimiter.addMinuteCount(userId, acceptedCount);

        int pointsAwarded = 0;
        long balance = pointAccountService.getBalance(userId);

        // "조용히 미집계": bot-suspected taps are not added to validTapCount at all (per PRD §7.6).
        if (!botSuspected && acceptedCount > 0) {
            daily.addValidTaps(acceptedCount);
            int dailyCap = tapPolicyConfig.pointDailyCap();
            int awardIndex = 0;
            while (daily.hasReachedTarget() && daily.getPointEarnedAmount() < dailyCap) {
                UUID idempotencyKey = deterministicIdempotencyKey(batch.getPublicId(), awardIndex);
                PointAccount account = pointAccountService.credit(userId, 1);
                pointLedgerService.recordCredit(account, user, 1, CREDIT_REASON_TAP, idempotencyKey);

                int nextTarget = tapCurveCalculator.drawNextTarget(daily.getValidTapCount(), daily.getPointEarnedAmount() + 1, tapPolicyConfig);
                daily.awardPoint(nextTarget);

                balance = account.getBalance();
                pointsAwarded++;
                awardIndex++;
            }
            userTapDailyService.save(daily);
        }

        return new TapSubmitOutcome(acceptedCount, pointsAwarded, balance);
    }

    private UUID deterministicIdempotencyKey(UUID batchPublicId, int awardIndex) {
        return UUID.nameUUIDFromBytes((batchPublicId + "-" + awardIndex).getBytes(StandardCharsets.UTF_8));
    }

    private String requestHash(TapSubmitCommand command) {
        String raw = command.tapSessionId() + ":" + command.sequence() + ":" + command.submittedCount();
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 must be available", exception);
        }
    }
}
