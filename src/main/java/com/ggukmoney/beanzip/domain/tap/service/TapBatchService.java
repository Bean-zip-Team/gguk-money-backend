package com.ggukmoney.beanzip.domain.tap.service;

import com.ggukmoney.beanzip.domain.keycap.service.KeycapBoxAccountService;
import com.ggukmoney.beanzip.domain.point.entity.PointAccount;
import com.ggukmoney.beanzip.domain.point.service.PointAccountService;
import com.ggukmoney.beanzip.domain.point.service.PointLedgerService;
import com.ggukmoney.beanzip.domain.ranking.event.RankingScoreSyncRequestedEvent;
import com.ggukmoney.beanzip.global.config.TapPolicyConfig;
import com.ggukmoney.beanzip.domain.tap.dto.request.TapBatchSubmitRequest;
import com.ggukmoney.beanzip.domain.tap.dto.response.TapBatchSubmitResponse;
import com.ggukmoney.beanzip.domain.tap.entity.TapBatch;
import com.ggukmoney.beanzip.domain.tap.entity.UserTapDaily;
import com.ggukmoney.beanzip.domain.tap.entity.UserTapProgress;
import com.ggukmoney.beanzip.domain.tap.entity.UserTapSession;
import com.ggukmoney.beanzip.domain.tap.repository.TapBatchRepository;
import com.ggukmoney.beanzip.domain.user.entity.AppUser;
import com.ggukmoney.beanzip.domain.user.service.UserService;
import com.ggukmoney.beanzip.global.service.RedisService;
import com.ggukmoney.beanzip.global.util.TokenHash;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TapBatchService {

    private static final Logger log = LoggerFactory.getLogger(TapBatchService.class);

    private static final String CREDIT_REASON_TAP = "TAP_REWARD";

    private static final RedisScript<Long> TOKEN_BUCKET_SCRIPT =
            RedisScript.of(new ClassPathResource("scripts/tap-token-bucket.lua"), Long.class);

    private final TapBatchRepository tapBatchRepository;
    private final UserTapDailyService userTapDailyService;
    private final UserTapProgressService userTapProgressService;
    private final UserTapSessionService userTapSessionService;
    private final PointAccountService pointAccountService;
    private final PointLedgerService pointLedgerService;
    private final KeycapBoxAccountService keycapBoxAccountService;
    private final RedisService redisService;
    private final TapPolicyConfig tapPolicyConfig;
    private final UserService userService;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;
    private final ZoneId businessZoneId;

    @Transactional
    public TapBatchSubmitResponse submitBatch(UUID userId, TapBatchSubmitRequest request) {
        Instant acceptedAt = clock.instant();
        LocalDate tapDate = LocalDate.ofInstant(acceptedAt, businessZoneId);
        if (tapPolicyConfig.rateLimitEnabled()
                && !tryConsumeRateLimit(userId, tapPolicyConfig.rateLimitCapacity(), tapPolicyConfig.rateLimitRefillPerSecond(), acceptedAt)) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "TAP_RATE_LIMITED");
        }

        AppUser user = userService.getById(userId);

        Optional<TapBatch> existing = tapBatchRepository.findByUserIdAndTapSessionIdAndSequence(userId, request.tapSessionId(), request.sequence());
        if (existing.isPresent()) {
            long balance = pointAccountService.getBalance(userId);
            UserTapDaily existingDaily = userTapDailyService.getOrCreate(user, tapDate);
            return new TapBatchSubmitResponse(existing.get().getAcceptedCount(), existingDaily.getValidTapCount(), 0, 0, balance, false);
        }

        UserTapDaily daily = userTapDailyService.getOrCreate(user, tapDate);
        int acceptedCount = request.submittedCount();

        TapBatch batch = TapBatch.createFor(user, request.tapSessionId(), request.sequence(), request.submittedCount(), requestHash(request));
        batch.markAccepted(acceptedCount);
        batch = tapBatchRepository.save(batch);

        int pointsAwarded = 0;
        int boxesDropped = 0;
        long balance = pointAccountService.getBalance(userId);

        if (acceptedCount > 0) {
            int remainingDailyTapAllowance = Math.max(tapPolicyConfig.maxPerDay() - daily.getValidTapCount(), 0);
            int creditedTaps = Math.min(acceptedCount, remainingDailyTapAllowance);

            if (creditedTaps > 0) {
                daily.addValidTaps(creditedTaps);
                UserTapProgress progress = userTapProgressService.getForUser(userId);
                progress.addValidTaps(creditedTaps);

                long creditAmount = 1L;

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

                UserTapSession session = userTapSessionService.getOrCreateActiveSession(user, acceptedAt, tapPolicyConfig);
                session.addValidTaps(creditedTaps);
                while (session.hasReachedBoxTarget()) {
                    keycapBoxAccountService.addBoxes(userId, 1);

                    int nextBoxTarget = userTapSessionService.drawNextBoxTargetInSession(session.getSessionValidTapCount(), session.getBoxesDroppedInSession(), tapPolicyConfig);
                    session.advanceBoxTarget(nextBoxTarget);

                    boxesDropped++;
                }
                userTapSessionService.save(session);

                userTapDailyService.save(daily);
                userTapProgressService.save(progress);
                eventPublisher.publishEvent(new RankingScoreSyncRequestedEvent(userId, acceptedAt));
            }
        }

        boolean pointDailyCapReached = daily.getPointEarnedAmount() >= tapPolicyConfig.pointDailyCap();
        return new TapBatchSubmitResponse(acceptedCount, daily.getValidTapCount(), pointsAwarded, boxesDropped, balance, pointDailyCapReached);
    }

    private UUID deterministicIdempotencyKey(UUID batchPublicId, int awardIndex) {
        return UUID.nameUUIDFromBytes((batchPublicId + "-" + awardIndex).getBytes(StandardCharsets.UTF_8));
    }

    private String requestHash(TapBatchSubmitRequest request) {
        String raw = request.tapSessionId() + ":" + request.sequence() + ":" + request.submittedCount();
        return TokenHash.sha256Base64Url(raw);
    }

    boolean tryConsumeRateLimit(UUID userId, int capacity, double refillPerSecond, Instant now) {
        try {
            Long allowed = redisService.executeScript(
                    TOKEN_BUCKET_SCRIPT,
                    List.of(bucketKey(userId)),
                    String.valueOf(capacity),
                    String.valueOf(refillPerSecond),
                    String.valueOf(now.toEpochMilli())
            );
            return allowed != null && allowed == 1L;
        } catch (RuntimeException exception) {
            log.error("Failed to evaluate tap rate limit for userId={}", userId, exception);
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "TAP_REDIS_UNAVAILABLE", exception);
        }
    }

    private String bucketKey(UUID userId) {
        return "tap:bucket:" + userId;
    }
}
