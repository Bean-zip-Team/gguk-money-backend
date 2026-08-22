package com.ggukmoney.beanzip.domain.tap.service;

import com.ggukmoney.beanzip.domain.keycap.entity.KeycapBoxAccount;
import com.ggukmoney.beanzip.domain.keycap.service.KeycapBoxAccountService;
import com.ggukmoney.beanzip.domain.point.entity.PointAccount;
import com.ggukmoney.beanzip.domain.point.service.PointAccountService;
import com.ggukmoney.beanzip.domain.point.service.PointLedgerService;
import com.ggukmoney.beanzip.domain.ranking.event.RankingScoreSyncRequestedEvent;
import com.ggukmoney.beanzip.global.config.KeycapBoxPolicyConfig;
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
    private final KeycapBoxPolicyConfig keycapBoxPolicyConfig;
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

        // 보상 상태는 지급 여부와 무관하게 응답에 담기므로 여기서 한 번만 조회한다.
        // 지급 루프 안에서 다시 조회하면 파생 쿼리라 1차 캐시로 대체되지 않고
        // 반복마다 SELECT와 auto-flush UPDATE가 발생한다.
        UserTapDaily daily = userTapDailyService.getOrCreate(user, tapDate);
        UserTapSession session = userTapSessionService.getOrCreateActiveSession(user, acceptedAt, tapPolicyConfig);
        UserTapProgress progress = userTapProgressService.getForUser(userId);
        PointAccount pointAccount = pointAccountService.getForUser(userId);
        KeycapBoxAccount boxAccount = keycapBoxAccountService.getForUser(userId);

        Optional<TapBatch> existing = tapBatchRepository.findByUserIdAndTapSessionIdAndSequence(userId, request.tapSessionId(), request.sequence());
        if (existing.isPresent()) {
            return buildResponse(existing.get().getAcceptedCount(), 0, 0, acceptedAt, daily, session, progress, pointAccount, boxAccount);
        }

        int acceptedCount = request.submittedCount();

        TapBatch batch = TapBatch.createFor(user, request.tapSessionId(), request.sequence(), request.submittedCount(), requestHash(request));
        batch.markAccepted(acceptedCount);
        batch = tapBatchRepository.save(batch);

        int pointsAwarded = 0;
        int boxesDropped = 0;

        if (acceptedCount > 0) {
            daily.addTotalValidTaps(acceptedCount);

            int remainingDailyTapAllowance = Math.max(tapPolicyConfig.maxPerDay() - daily.getValidTapCount(), 0);
            int creditedTaps = Math.min(acceptedCount, remainingDailyTapAllowance);

            if (creditedTaps > 0) {
                daily.addValidTaps(creditedTaps);
                progress.addValidTaps(creditedTaps);

                long creditAmount = 1L;

                int dailyCap = tapPolicyConfig.pointDailyCap();
                int awardIndex = 0;
                while (progress.hasReachedPointTarget() && daily.getPointEarnedAmount() < dailyCap) {
                    UUID idempotencyKey = deterministicIdempotencyKey(batch.getPublicId(), awardIndex);
                    pointAccount.credit(creditAmount);
                    pointLedgerService.recordCredit(pointAccount, user, creditAmount, CREDIT_REASON_TAP, idempotencyKey);
                    daily.incrementPointEarned();

                    int nextTarget = userTapProgressService.drawNextTarget(progress.getCumulativeValidTapCount(), daily.getPointEarnedAmount(), tapPolicyConfig);
                    progress.advancePointTarget(nextTarget);

                    pointsAwarded += creditAmount;
                    awardIndex++;
                }

                userTapProgressService.save(progress);
                if (pointsAwarded > 0) {
                    pointAccountService.save(pointAccount);
                }
            }

            // 상자 진행도는 포인트 일일 상한(tap.validity.maxPerDay)과 무관하게 인정된 탭 전체로 누적한다.
            // 상자 획득 속도의 실질 병목은 재고가 아니라 개봉 주기(무료 2회·광고 2회)이므로 여기서 막지 않는다.
            session.addValidTaps(acceptedCount);
            while (session.hasReachedBoxTarget()) {
                boxAccount.addBoxes(1);

                int nextBoxTarget = userTapSessionService.drawNextBoxTargetInSession(session.getSessionValidTapCount(), session.getBoxesDroppedInSession(), tapPolicyConfig);
                session.advanceBoxTarget(nextBoxTarget);

                boxesDropped++;
            }
            if (boxesDropped > 0) {
                keycapBoxAccountService.save(boxAccount);
            }
            userTapSessionService.save(session);

            userTapDailyService.save(daily);
            eventPublisher.publishEvent(new RankingScoreSyncRequestedEvent(userId, acceptedAt));
        }

        return buildResponse(acceptedCount, pointsAwarded, boxesDropped, acceptedAt, daily, session, progress, pointAccount, boxAccount);
    }

    /**
     * 배치 확정 직후 화면을 그리는 데 필요한 값을 모두 담는다. 상자 개봉 가능 여부는
     * 이번 배치의 지급까지 반영된 잔고 기준이어야 하므로 지급이 끝난 뒤 계산한다.
     */
    private TapBatchSubmitResponse buildResponse(
            int acceptedCount,
            int pointsAwarded,
            int boxesDropped,
            Instant now,
            UserTapDaily daily,
            UserTapSession session,
            UserTapProgress progress,
            PointAccount pointAccount,
            KeycapBoxAccount boxAccount
    ) {
        int remainingToNextPoint = (int) Math.max(progress.getNextPointTarget() - progress.getCumulativeValidTapCount(), 0);
        int remainingToNextBox = (int) Math.max(session.getNextBoxTarget() - session.getSessionValidTapCount(), 0);
        KeycapBoxAccount.OpenCycleSnapshot cycleSnapshot = boxAccount.calculateOpenCycleSnapshot(
                now,
                keycapBoxPolicyConfig.openCycleDuration(),
                keycapBoxPolicyConfig.freeOpenLimit(),
                keycapBoxPolicyConfig.adOpenLimit()
        );

        // 화면의 "오늘 탭"은 보상 상한(tap.validity.maxPerDay)과 무관하게 실제로 친 탭 수를 보여준다.
        // validTapCount 는 상한에서 멈추므로 상한 없이 누적되는 totalValidTapCount 를 반환한다.
        return new TapBatchSubmitResponse(
                acceptedCount,
                daily.getTotalValidTapCount(),
                pointsAwarded,
                boxesDropped,
                pointAccount.getBalance(),
                daily.getPointEarnedAmount() >= tapPolicyConfig.pointDailyCap(),
                session.getSessionValidTapCount(),
                session.getNextBoxTarget(),
                daily.getTapDate(),
                daily.getPointEarnedAmount(),
                remainingToNextPoint,
                remainingToNextBox,
                boxAccount.getBoxBalance(),
                cycleSnapshot.canFreeOpen(),
                cycleSnapshot.canAdOpen(),
                cycleSnapshot.charging(),
                cycleSnapshot.nextRechargeAt()
        );
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
        return "ggukmoney:tap:bucket:" + userId;
    }
}
