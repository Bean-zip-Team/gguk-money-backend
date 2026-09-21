package com.ggukmoney.beanzip.domain.promotion.service;

import com.ggukmoney.beanzip.domain.auth.entity.AuthIdentity;
import com.ggukmoney.beanzip.domain.auth.repository.AuthIdentityRepository;
import com.ggukmoney.beanzip.domain.promotion.config.TossPromotionCodeRegistry;
import com.ggukmoney.beanzip.domain.promotion.entity.PromotionGrant;
import com.ggukmoney.beanzip.global.client.toss.TossPromotionClient;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 지급 실행. <b>트랜잭션 밖에서</b> 돈다.
 *
 * <p>순서가 중요하다. get-key 결과를 먼저 커밋하고 나서 execute 를 부른다. 그래야 모호 실패
 * 이후 재시도가 같은 key 를 재사용하고, 토스가 4113 으로 멱등을 보장해준다.
 *
 * <p>이 설계는 토스가 key 단위로 멱등하고 콘솔의 1인 1회 제한이 서버측에서 강제된다는 가정
 * 위에 있다. 둘 중 하나라도 아니면 순서를 되돌려야 한다.
 */
@Service
@RequiredArgsConstructor
public class PromotionExecutionService {

    private static final Logger log = LoggerFactory.getLogger(PromotionExecutionService.class);

    private static final int MAX_ATTEMPTS = 10;
    private static final Duration BASE_BACKOFF = Duration.ofSeconds(30);
    private static final Duration MAX_BACKOFF = Duration.ofMinutes(15);
    private static final Duration CONFIG_ERROR_BACKOFF = Duration.ofMinutes(5);
    private static final Duration WALLET_EMPTY_BACKOFF = Duration.ofMinutes(30);
    private static final Duration POLL_BACKOFF = Duration.ofSeconds(30);

    private static final String HOLD_KEY_COMMIT_FAILED = "KEY_COMMIT_FAILED";

    private final TossPromotionClient tossPromotionClient;
    private final TossPromotionCodeRegistry tossPromotionCodeRegistry;
    private final PromotionGrantStateService stateService;
    private final PromotionErrorPolicy errorPolicy;
    private final AuthIdentityRepository authIdentityRepository;
    private final Clock clock;

    /** @return 지갑이 비어 이번 tick 의 남은 execute 를 중단해야 하면 true */
    public boolean execute(Long grantId) {
        PromotionGrant grant = stateService.find(grantId).orElse(null);
        if (grant == null) {
            return false;
        }
        if (grant.getStatus() == PromotionGrant.Status.PROCESSING) {
            poll(grant);
            return false;
        }
        return runExecute(grant);
    }

    private boolean runExecute(PromotionGrant grant) {
        Instant now = Instant.now(clock);
        Long grantId = grant.getId();
        UUID userId = grant.getUser().getId();

        String promotionCode = tossPromotionCodeRegistry.tossCodeOf(grant.getPromotionCode()).orElse(null);
        if (promotionCode == null || promotionCode.isBlank()) {
            // 설정 오류다. 지급 실패로 태우지 않는다. attempt 도 소모하지 않되 백오프는 건다.
            log.error("Toss promotion code is not configured; promotionCode={} grantId={}",
                    grant.getPromotionCode(), grantId);
            stateService.deferWithoutAttempt(grantId, now.plus(CONFIG_ERROR_BACKOFF), "NOT_CONFIGURED", now);
            return false;
        }

        Optional<String> tossUserKey = resolveTossUserKey(userId);
        if (tossUserKey.isEmpty()) {
            // 탈퇴했거나 연동이 해제됐다. 사람이 볼 게 없는 행이므로 확정 실패로 닫는다.
            log.warn("No Toss identity for promotion grant; grantId={} userId={}", grantId, userId);
            stateService.markFailed(grantId, null, "NO_TOSS_IDENTITY", now);
            return false;
        }

        if (!grant.hasTossKey()) {
            String key;
            try {
                key = tossPromotionClient.getKey(tossUserKey.get());
            } catch (RuntimeException exception) {
                log.warn("Toss get-key failed; grantId={}", grantId, exception);
                stateService.deferAttempt(grantId, nextBackoff(grant, now), "GET_KEY_FAILED", now);
                return false;
            }
            try {
                stateService.assignKey(grantId, key, promotionCode, now);
            } catch (RuntimeException exception) {
                // key 는 발급됐는데 저장에 실패했다. 재시도하면 새 key 를 받게 되고 그건
                // 이중지급 경로다. 자동 진행에서 빼고 사람이 확인한다.
                log.error("Failed to persist Toss promotion key; holding. grantId={}", grantId, exception);
                stateService.hold(grantId, HOLD_KEY_COMMIT_FAILED, now);
                return false;
            }
            grant = stateService.find(grantId).orElse(grant);
        }

        try {
            TossPromotionClient.PromotionExecutionOutcome outcome = tossPromotionClient.executePromotion(
                    tossUserKey.get(), promotionCode, grant.getTossPromotionKey(), grant.getAmount());
            if (outcome.succeeded()) {
                stateService.markProcessing(grantId, now);
                return false;
            }
            return applyErrorDecision(grant, outcome.tossErrorCode(), outcome.reason(), now);
        } catch (TossPromotionClient.AmbiguousTossFailureException exception) {
            // 토스가 처리했는지 알 수 없다. 같은 key 로 재시도한다. 절대 실패로 확정하지 않는다.
            log.warn("Toss execute-promotion ambiguous; retrying with same key. grantId={}", grantId, exception);
            stateService.deferAttempt(grantId, nextBackoff(grant, now), "AMBIGUOUS", now);
            return false;
        } catch (ResponseStatusException exception) {
            // 클라이언트 설정 문제(base-url/코드 미설정). 지급 실패가 아니다.
            log.error("Toss client not usable; grantId={} reason={}", grantId, exception.getReason());
            stateService.deferWithoutAttempt(grantId, now.plus(CONFIG_ERROR_BACKOFF), exception.getReason(), now);
            return false;
        }
    }

    private boolean applyErrorDecision(PromotionGrant grant, String errorCode, String reason, Instant now) {
        Long grantId = grant.getId();
        PromotionErrorPolicy.Decision decision = errorPolicy.decide(errorCode);
        switch (decision) {
            case SUCCEED -> {
                log.info("Toss reports already granted; converging to SUCCEEDED. grantId={}", grantId);
                stateService.markSucceeded(grantId, now);
            }
            case WALLET_EMPTY -> {
                // 지갑이 빌 동안 완성한 유저 전원을 영구 미지급으로 굳히면 안 된다.
                log.error("Toss promotion wallet is empty; deferring. grantId={}", grantId);
                stateService.deferAttempt(grantId, now.plus(WALLET_EMPTY_BACKOFF), errorCode, now);
                return true;
            }
            case FAIL -> stateService.markFailed(grantId, errorCode, reason, now);
            case REVIEW -> {
                log.error("Unknown Toss errorCode; flagging for review. grantId={} errorCode={}", grantId, errorCode);
                stateService.deferAttempt(grantId, nextBackoff(grant, now), errorCode, now);
                stateService.flagForReview(grantId, now);
            }
        }
        return false;
    }

    /**
     * 최종 지급 여부 확인.
     *
     * <p>{@code getExecutionResult} 는 4xx·5xx·네트워크·빈 응답을 전부 BAD_GATEWAY 로 뭉갠다.
     * 그래서 여기서는 "모르는 값"과 "네트워크 오류"를 구분할 수 없고, 예외는 무조건 재시도다.
     * 명시적으로 FAILED 를 받았을 때만 실패로 확정한다.
     */
    void poll(PromotionGrant grant) {
        Instant now = Instant.now(clock);
        Long grantId = grant.getId();

        Optional<String> tossUserKey = resolveTossUserKey(grant.getUser().getId());
        if (tossUserKey.isEmpty()) {
            stateService.markFailed(grantId, null, "NO_TOSS_IDENTITY", now);
            return;
        }

        try {
            TossPromotionClient.PromotionResultStatus status = tossPromotionClient.getExecutionResult(
                    tossUserKey.get(), grant.getTossPromotionCode(), grant.getTossPromotionKey());
            switch (status) {
                case SUCCESS -> stateService.markSucceeded(grantId, now);
                case FAILED -> stateService.markFailed(grantId, null, "TOSS_RESULT_FAILED", now);
                default -> stateService.deferPoll(grantId, now.plus(POLL_BACKOFF), now);
            }
        } catch (RuntimeException exception) {
            log.warn("Toss execution-result poll failed; retrying. grantId={}", grantId, exception);
            stateService.deferPoll(grantId, now.plus(POLL_BACKOFF), now);
        }
    }

    private Optional<String> resolveTossUserKey(UUID userId) {
        return authIdentityRepository.findByUserIdAndProvider(userId, AuthIdentity.Provider.TOSS)
                .map(AuthIdentity::getProviderUserId)
                .filter(value -> value != null && !value.isBlank());
    }

    /** 지수 백오프에 지터를 섞는다. 상한을 넘기면 자동 진행을 멈추고 사람이 본다. */
    private Instant nextBackoff(PromotionGrant grant, Instant now) {
        int attempt = grant.getAttemptCount() == null ? 0 : grant.getAttemptCount();
        if (attempt + 1 >= MAX_ATTEMPTS) {
            stateService.hold(grant.getId(), "MAX_ATTEMPTS_EXCEEDED", now);
        }
        long seconds = Math.min(
                BASE_BACKOFF.toSeconds() * (1L << Math.min(attempt, 20)),
                MAX_BACKOFF.toSeconds());
        long jitter = ThreadLocalRandom.current().nextLong(0, Math.max(1, seconds / 4));
        return now.plusSeconds(seconds + jitter);
    }
}
