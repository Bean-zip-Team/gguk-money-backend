package com.ggukmoney.beanzip.domain.tap.service;

import com.ggukmoney.beanzip.domain.booster.service.BoosterGrantService;
import com.ggukmoney.beanzip.domain.keycap.service.KeycapBoxAccountService;
import com.ggukmoney.beanzip.domain.point.entity.PointAccount;
import com.ggukmoney.beanzip.domain.point.service.PointAccountService;
import com.ggukmoney.beanzip.domain.point.service.PointLedgerService;
import com.ggukmoney.beanzip.domain.ranking.service.RankingProjectionService;
import com.ggukmoney.beanzip.global.config.TapPolicyConfig;
import com.ggukmoney.beanzip.domain.tap.dto.request.TapBatchSubmitRequest;
import com.ggukmoney.beanzip.domain.tap.dto.response.TapBatchSubmitResponse;
import com.ggukmoney.beanzip.domain.tap.entity.TapBatch;
import com.ggukmoney.beanzip.domain.tap.entity.UserTapDaily;
import com.ggukmoney.beanzip.domain.tap.entity.UserTapProgress;
import com.ggukmoney.beanzip.domain.tap.repository.TapBatchRepository;
import com.ggukmoney.beanzip.domain.user.entity.AppUser;
import com.ggukmoney.beanzip.domain.user.service.UserService;
import com.ggukmoney.beanzip.global.service.RedisService;
import com.ggukmoney.beanzip.global.util.TokenHash;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TapBatchService {

    private static final Logger log = LoggerFactory.getLogger(TapBatchService.class);

    private static final String CREDIT_REASON_TAP = "TAP_REWARD";
    private static final int MIN_BOT_SAMPLE_SIZE = 3;
    private static final Duration MINUTE_WINDOW = Duration.ofSeconds(60);

    private static final RedisScript<Long> TOKEN_BUCKET_SCRIPT =
            RedisScript.of(new ClassPathResource("scripts/tap-token-bucket.lua"), Long.class);
    private static final RedisScript<Long> INCR_WITH_WINDOW_SCRIPT =
            RedisScript.of(new ClassPathResource("scripts/tap-incr-with-window.lua"), Long.class);

    private final TapBatchRepository tapBatchRepository;
    private final UserTapDailyService userTapDailyService;
    private final UserTapProgressService userTapProgressService;
    private final PointAccountService pointAccountService;
    private final PointLedgerService pointLedgerService;
    private final KeycapBoxAccountService keycapBoxAccountService;
    private final BoosterGrantService boosterGrantService;
    private final RedisService redisService;
    private final TapPolicyConfig tapPolicyConfig;
    private final UserService userService;
    private final RankingProjectionService rankingProjectionService;

    @Transactional
    public TapBatchSubmitResponse submitBatch(UUID userId, TapBatchSubmitRequest request) {
        if (!tryConsumeRateLimit(userId, tapPolicyConfig.rateLimitCapacity(), tapPolicyConfig.rateLimitRefillPerSecond())) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "TAP_RATE_LIMITED");
        }

        Optional<TapBatch> existing = tapBatchRepository.findByUserIdAndTapSessionIdAndSequence(userId, request.tapSessionId(), request.sequence());
        if (existing.isPresent()) {
            long balance = pointAccountService.getBalance(userId);
            return new TapBatchSubmitResponse(existing.get().getAcceptedCount(), 0, 0, balance);
        }

        AppUser user = userService.getById(userId);

        Instant now = Instant.now();
        List<TapBatch> recentBatches = tapBatchRepository.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(0, tapPolicyConfig.botSampleSize()));
        Duration elapsedSinceLastBatch = recentBatches.isEmpty()
                ? null
                : Duration.between(recentBatches.get(0).getCreatedAt(), now);

        int minuteRemaining = tapPolicyConfig.maxPerMinute() - getMinuteCount(userId);

        UserTapDaily daily = userTapDailyService.getOrCreateToday(user);
        int dailyRemaining = tapPolicyConfig.maxPerDay() - daily.getValidTapCount();

        int acceptedCount = calculateAcceptedCount(
                request.submittedCount(), elapsedSinceLastBatch, minuteRemaining, dailyRemaining, tapPolicyConfig
        );

        List<Instant> botCheckTimestamps = new ArrayList<>();
        botCheckTimestamps.add(now);
        recentBatches.forEach(batch -> botCheckTimestamps.add(batch.getCreatedAt()));
        boolean botSuspected = isSuspicious(botCheckTimestamps, tapPolicyConfig);

        TapBatch batch = TapBatch.createFor(user, request.tapSessionId(), request.sequence(), request.submittedCount(), requestHash(request));
        batch.markAccepted(acceptedCount);
        if (botSuspected) {
            batch.markBotSuspected();
        }
        batch = tapBatchRepository.save(batch);

        addMinuteCount(userId, acceptedCount);

        int pointsAwarded = 0;
        int boxesDropped = 0;
        long balance = pointAccountService.getBalance(userId);

        if (!botSuspected && acceptedCount > 0) {
            daily.addValidTaps(acceptedCount);
            UserTapProgress progress = userTapProgressService.getForUser(userId);
            progress.addValidTaps(acceptedCount);
            rankingProjectionService.syncAllTimeScore(userId, progress.getCumulativeValidTapCount());

            BigDecimal boosterMultiplier = boosterGrantService.findActiveMultiplier(userId, now);
            long creditAmount = BigDecimal.ONE.multiply(boosterMultiplier).setScale(0, RoundingMode.DOWN).longValueExact();

            int dailyCap = tapPolicyConfig.pointDailyCap();
            int awardIndex = 0;
            while (progress.hasReachedPointTarget() && daily.getPointEarnedAmount() < dailyCap) {
                UUID idempotencyKey = deterministicIdempotencyKey(batch.getPublicId(), awardIndex);
                PointAccount account = pointAccountService.credit(userId, creditAmount);
                pointLedgerService.recordCredit(account, user, creditAmount, CREDIT_REASON_TAP, idempotencyKey);
                daily.incrementPointEarned();

                int nextTarget = userTapProgressService.drawNextTarget(progress.getCumulativeValidTapCount(), daily.getPointEarnedAmount(), tapPolicyConfig);
                progress.advancePointTarget(nextTarget);

                balance = account.getBalance();
                pointsAwarded += creditAmount;
                awardIndex++;
            }

            while (progress.hasReachedBoxTarget()) {
                keycapBoxAccountService.addBoxes(userId, 1);

                int nextBoxTarget = userTapProgressService.drawNextBoxTarget(progress.getCumulativeValidTapCount(), tapPolicyConfig);
                progress.advanceBoxTarget(nextBoxTarget);

                boxesDropped++;
            }

            userTapDailyService.save(daily);
            userTapProgressService.save(progress);
        }

        return new TapBatchSubmitResponse(acceptedCount, pointsAwarded, boxesDropped, balance);
    }

    private int calculateAcceptedCount(
            int submittedCount,
            Duration elapsedSinceLastBatch,
            int minuteRemaining,
            int dailyRemaining,
            TapPolicyConfig config
    ) {
        int accepted = Math.min(submittedCount, elapsedBasedCap(elapsedSinceLastBatch, config.minIntervalMs()));
        accepted = Math.min(accepted, Math.max(minuteRemaining, 0));
        accepted = Math.min(accepted, Math.max(dailyRemaining, 0));
        return Math.max(accepted, 0);
    }

    private int elapsedBasedCap(Duration elapsedSinceLastBatch, int minIntervalMs) {
        if (elapsedSinceLastBatch == null) {
            return Integer.MAX_VALUE;
        }
        long elapsedMillis = elapsedSinceLastBatch.toMillis();
        if (elapsedMillis <= 0) {
            return 0;
        }
        return (int) (elapsedMillis / minIntervalMs);
    }

    private boolean isSuspicious(List<Instant> timestampsMostRecentFirst, TapPolicyConfig config) {
        if (timestampsMostRecentFirst.size() < MIN_BOT_SAMPLE_SIZE) {
            return false;
        }

        double[] gapsMillis = new double[timestampsMostRecentFirst.size() - 1];
        for (int i = 0; i < gapsMillis.length; i++) {
            long gap = timestampsMostRecentFirst.get(i).toEpochMilli() - timestampsMostRecentFirst.get(i + 1).toEpochMilli();
            gapsMillis[i] = Math.abs(gap);
        }

        double stddev = standardDeviation(gapsMillis);
        return stddev < config.botStddevThresholdMs();
    }

    private double standardDeviation(double[] values) {
        double mean = 0;
        for (double value : values) {
            mean += value;
        }
        mean /= values.length;

        double variance = 0;
        for (double value : values) {
            variance += Math.pow(value - mean, 2);
        }
        variance /= values.length;

        return Math.sqrt(variance);
    }

    private UUID deterministicIdempotencyKey(UUID batchPublicId, int awardIndex) {
        return UUID.nameUUIDFromBytes((batchPublicId + "-" + awardIndex).getBytes(StandardCharsets.UTF_8));
    }

    private String requestHash(TapBatchSubmitRequest request) {
        String raw = request.tapSessionId() + ":" + request.sequence() + ":" + request.submittedCount();
        return TokenHash.sha256Base64Url(raw);
    }

    boolean tryConsumeRateLimit(UUID userId, int capacity, double refillPerSecond) {
        try {
            Long allowed = redisService.executeScript(
                    TOKEN_BUCKET_SCRIPT,
                    List.of(bucketKey(userId)),
                    String.valueOf(capacity),
                    String.valueOf(refillPerSecond),
                    String.valueOf(Instant.now().toEpochMilli())
            );
            return allowed != null && allowed == 1L;
        } catch (RuntimeException exception) {
            log.error("Failed to evaluate tap rate limit for userId={}", userId, exception);
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "TAP_REDIS_UNAVAILABLE", exception);
        }
    }

    int getMinuteCount(UUID userId) {
        return redisService.get(minuteKey(userId)).map(Integer::parseInt).orElse(0);
    }

    void addMinuteCount(UUID userId, int delta) {
        if (delta <= 0) {
            return;
        }
        try {
            redisService.executeScript(
                    INCR_WITH_WINDOW_SCRIPT,
                    List.of(minuteKey(userId)),
                    String.valueOf(delta),
                    String.valueOf(MINUTE_WINDOW.toSeconds())
            );
        } catch (RuntimeException exception) {
            log.error("Failed to increment tap minute counter for userId={}", userId, exception);
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "TAP_REDIS_UNAVAILABLE", exception);
        }
    }

    private String bucketKey(UUID userId) {
        return "tap:bucket:" + userId;
    }

    private String minuteKey(UUID userId) {
        return "tap:minute:" + userId;
    }
}
