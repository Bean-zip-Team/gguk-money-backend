package com.ggukmoney.beanzip.domain.cashout.service;

import com.ggukmoney.beanzip.domain.auth.entity.AuthIdentity;
import com.ggukmoney.beanzip.domain.auth.repository.AuthIdentityRepository;
import com.ggukmoney.beanzip.global.client.toss.TossPromotionClient;
import com.ggukmoney.beanzip.domain.cashout.dto.response.CashoutListItemResponse;
import com.ggukmoney.beanzip.domain.cashout.dto.response.CashoutListPageResponse;
import com.ggukmoney.beanzip.domain.cashout.dto.response.CashoutQuoteResponse;
import com.ggukmoney.beanzip.domain.cashout.dto.response.CashoutSubmitResponse;
import com.ggukmoney.beanzip.domain.cashout.entity.CashoutRequest;
import com.ggukmoney.beanzip.domain.cashout.repository.CashoutRequestRepository;
import com.ggukmoney.beanzip.domain.point.entity.PointAccount;
import com.ggukmoney.beanzip.domain.point.service.PointAccountService;
import com.ggukmoney.beanzip.domain.point.service.PointLedgerService;
import com.ggukmoney.beanzip.domain.user.entity.AppUser;
import com.ggukmoney.beanzip.domain.user.service.UserService;
import com.ggukmoney.beanzip.global.config.CashoutPolicyConfig;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CashoutService {

    private static final String DEBIT_REASON_CASHOUT = "CASHOUT";
    private static final String REVERSAL_REASON_CASHOUT_FAILED = "CASHOUT_FAILED";
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;
    private static final EnumSet<CashoutRequest.Status> TERMINAL_STATUSES =
            EnumSet.of(CashoutRequest.Status.SUCCEEDED, CashoutRequest.Status.FAILED, CashoutRequest.Status.CANCELED);

    private final PointAccountService pointAccountService;
    private final PointLedgerService pointLedgerService;
    private final CashoutRequestRepository cashoutRequestRepository;
    private final AuthIdentityRepository authIdentityRepository;
    private final TossPromotionClient tossPromotionClient;
    private final UserService userService;
    private final CashoutPolicyConfig cashoutPolicyConfig;
    private final PlatformTransactionManager transactionManager;

    @Value("${app.cashout.toss.promotion-code:}")
    private String promotionCode;

    public CashoutQuoteResponse getQuote(UUID userId) {
        long balance = pointAccountService.getBalance(userId);
        int minimumPoint = cashoutPolicyConfig.minimumPoint();
        BigDecimal rate = cashoutPolicyConfig.pointToKrwRate();
        long tossPointAmount = toTossPointAmount(balance, rate);
        long redeemablePoint = toRedeemablePoint(tossPointAmount, rate);
        boolean eligible = balance >= minimumPoint && redeemablePoint > 0;

        return new CashoutQuoteResponse(
                balance,
                tossPointAmount,
                redeemablePoint,
                balance - redeemablePoint,
                minimumPoint,
                new CashoutQuoteResponse.RateInfo(rate),
                eligible
        );
    }

    public CashoutSubmitResponse submit(UUID userId, UUID idempotencyKey) {
        Optional<CashoutSubmitResponse> replay = findReplay(userId, idempotencyKey);
        if (replay.isPresent()) {
            return replay.get();
        }

        CashoutRequest request;
        try {
            TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
            request = transactionTemplate.execute(status -> createRequestAndDebit(userId, idempotencyKey));
        } catch (DataIntegrityViolationException exception) {
            return findReplay(userId, idempotencyKey).orElseThrow(() -> exception);
        } catch (OptimisticLockingFailureException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "CASHOUT_ALREADY_PROCESSING", exception);
        }

        AppUser user = userService.getById(userId);
        submitToToss(request, userId, user, request.getPointAmount(), idempotencyKey);

        return toResponse(request);
    }

    private long toTossPointAmount(long balance, BigDecimal rate) {
        return BigDecimal.valueOf(balance).multiply(rate).setScale(0, RoundingMode.FLOOR).longValueExact();
    }

    /**
     * 실제로 차감할 포인트. 버림으로 사라지는 잔돈을 유저에게 남긴다.
     *
     * <p>전에는 잔액 전부를 빼고 {@code floor(balance x rate)} 만 지급했다. rate 가 0.02 면
     * 99P 를 출금해도 1P 만 받고 나머지 49P 가 소멸했다 — 어디에도 기록이 남지 않는 손실이다.
     * 이제 같은 지급액을 만드는 <b>최소 포인트만</b> 차감하고 나머지는 잔액으로 둔다.
     *
     * <p>{@code ceil(tossPointAmount / rate)} 이므로 rate 가 바뀌면 단위도 따라 바뀐다.
     * 0.02 면 50P 단위, 0.05 면 20P 단위다. 하드코딩하지 않는 이유가 이것이다.
     */
    private long toRedeemablePoint(long tossPointAmount, BigDecimal rate) {
        if (tossPointAmount <= 0 || rate.signum() <= 0) {
            return 0L;
        }
        return BigDecimal.valueOf(tossPointAmount)
                .divide(rate, 0, RoundingMode.CEILING)
                .longValueExact();
    }

    private Optional<CashoutSubmitResponse> findReplay(UUID userId, UUID idempotencyKey) {
        return cashoutRequestRepository.findByUserIdAndIdempotencyKey(userId, idempotencyKey)
                .map(this::toResponse);
    }

    /**
     * 잔액 차감 + 출금 신청 row 생성만 담당하는 짧은 트랜잭션. Toss 호출은 이 트랜잭션이 끝난 뒤
     * {@link #submitToToss}에서 별도로 수행한다 — DB 커넥션을 외부 API 응답 대기 동안 물고 있지
     * 않기 위함이자, Toss 호출 성공 이후 로컬 커밋 실패로 인한 롤백(이중지급 위험)을 피하기 위함이다.
     */
    @Transactional
    CashoutRequest createRequestAndDebit(UUID userId, UUID idempotencyKey) {
        if (cashoutRequestRepository.existsByUserIdAndStatusIn(
                userId, List.of(CashoutRequest.Status.REQUESTED, CashoutRequest.Status.PROCESSING))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "CASHOUT_ALREADY_PROCESSING");
        }

        long balance = pointAccountService.getBalance(userId);
        int minimumPoint = cashoutPolicyConfig.minimumPoint();
        if (balance < minimumPoint) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "CASHOUT_MINIMUM_NOT_MET");
        }

        AppUser user = userService.getById(userId);
        BigDecimal rate = cashoutPolicyConfig.pointToKrwRate();
        long tossPointAmount = toTossPointAmount(balance, rate);
        long redeemablePoint = toRedeemablePoint(tossPointAmount, rate);
        if (redeemablePoint <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "CASHOUT_MINIMUM_NOT_MET");
        }

        // 전액이 아니라 지급액을 만드는 만큼만 뺀다. 나머지는 잔액으로 남는다.
        PointAccount account = pointAccountService.debit(userId, redeemablePoint);
        pointLedgerService.recordDebit(account, user, redeemablePoint, DEBIT_REASON_CASHOUT, idempotencyKey);

        // pointAmount 가 실제 차감분이므로 실패 시 환불 금액도 자동으로 맞는다.
        return cashoutRequestRepository.save(
                CashoutRequest.createFor(user, redeemablePoint, tossPointAmount, idempotencyKey)
        );
    }

    /**
     * get-key + execute-promotion을 동기로 호출해 Toss에 지급을 접수한다. 실패 처리 방식은
     * PRD 논의에서 확정한 대로 다음과 같이 나뉜다:
     * - get-key 실패: Toss 쪽에서 아무 일도 일어나지 않았음이 확실하므로 안전하게 FAILED + 환불
     * - execute-promotion의 명시적 실패(4xx): 마찬가지로 안전하게 FAILED + 환불
     * - execute-promotion의 모호한 실패(네트워크 오류/5xx): 실제 처리 여부를 알 수 없으므로
     *   자동 환불하지 않고 REQUESTED로 남겨 운영자가 수동으로 확인하도록 한다(이중지급 위험 방지).
     */
    private void submitToToss(CashoutRequest request, UUID userId, AppUser user, long balance, UUID idempotencyKey) {
        log.info("Cashout Toss 제출 시작: cashoutId={}", request.getPublicId());
        AuthIdentity identity = authIdentityRepository.findByUserIdAndProvider(userId, AuthIdentity.Provider.TOSS)
                .orElse(null);
        if (identity == null) {
            log.error("출금 신청에 연결된 Toss 계정이 없음: cashoutId={}", request.getPublicId());
            persistFailedAndReverse(request, user, balance, idempotencyKey);
            return;
        }

        String key;
        try {
            key = tossPromotionClient.getKey(identity.getProviderUserId());
        } catch (RuntimeException exception) {
            log.error("Toss get-key 호출 실패, 환불 처리: cashoutId={}", request.getPublicId(), exception);
            persistFailedAndReverse(request, user, balance, idempotencyKey);
            return;
        }

        try {
            TossPromotionClient.PromotionExecutionOutcome outcome =
                    tossPromotionClient.executePromotion(identity.getProviderUserId(), promotionCode, key, request.getTossPointAmount());
            if (outcome.succeeded()) {
                persistProcessing(request, key);
            } else {
                log.warn("Toss execute-promotion 명시적 실패, 환불 처리: cashoutId={} errorCode={} reason={}",
                        request.getPublicId(), outcome.tossErrorCode(), outcome.reason());
                persistFailedAndReverse(request, user, balance, idempotencyKey);
            }
        } catch (TossPromotionClient.AmbiguousTossFailureException exception) {
            log.error("Toss execute-promotion 결과 불명 — 수동 확인 필요: cashoutId={}", request.getPublicId(), exception);
            // REQUESTED 상태 유지, 자동 환불하지 않음 (트랜잭션 A에서 이미 REQUESTED로 저장돼 있음)
        }
    }

    @Transactional
    void persistProcessing(CashoutRequest request, String tossPromotionKey) {
        request.markProcessing(tossPromotionKey);
        cashoutRequestRepository.save(request);
        log.info("Cashout PROCESSING 전이: cashoutId={} tossPromotionKey={} elapsedSinceRequestMs={}",
                request.getPublicId(), tossPromotionKey, elapsedSinceRequestMs(request));
    }

    @Transactional
    void persistFailedAndReverse(CashoutRequest request, AppUser user, long amount, UUID idempotencyKey) {
        request.markFailed();
        reverseDebit(request, user, amount, idempotencyKey);
        cashoutRequestRepository.save(request);
        log.info("Cashout FAILED 처리(제출 단계): cashoutId={} elapsedSinceRequestMs={}",
                request.getPublicId(), elapsedSinceRequestMs(request));
    }

    /**
     * {@code PROCESSING} 건의 execution-result 폴링 결과를 반영한다. {@code CashoutProcessingScheduler}에서 호출된다.
     */
    @Transactional
    public void finalizeProcessingCashout(CashoutRequest request, TossPromotionClient.PromotionResultStatus result) {
        switch (result) {
            case SUCCESS -> {
                request.markSucceeded();
                log.info("Cashout SUCCEEDED 확정: cashoutId={} elapsedSinceRequestMs={}",
                        request.getPublicId(), elapsedSinceRequestMs(request));
            }
            case FAILED -> {
                request.markFailed();
                reverseDebit(request, request.getUser(), request.getPointAmount(), request.getIdempotencyKey());
                log.info("Cashout FAILED 확정(환불 처리): cashoutId={} elapsedSinceRequestMs={}",
                        request.getPublicId(), elapsedSinceRequestMs(request));
            }
            case PENDING -> {
                // 다음 tick에 재확인
            }
        }
        cashoutRequestRepository.save(request);
    }

    private long elapsedSinceRequestMs(CashoutRequest request) {
        Instant createdAt = request.getCreatedAt();
        return createdAt == null ? -1 : Duration.between(createdAt, Instant.now()).toMillis();
    }

    private void reverseDebit(CashoutRequest request, AppUser user, long amount, UUID idempotencyKey) {
        PointAccount account = pointAccountService.credit(user.getId(), amount);
        UUID reversalIdempotencyKey = UUID.nameUUIDFromBytes((idempotencyKey + "-reversal").getBytes(StandardCharsets.UTF_8));
        pointLedgerService.recordReversal(account, user, amount, REVERSAL_REASON_CASHOUT_FAILED, reversalIdempotencyKey);
    }

    private CashoutSubmitResponse toResponse(CashoutRequest request) {
        return new CashoutSubmitResponse(
                request.getPublicId(),
                request.getPointAmount(),
                request.getTossPointAmount(),
                request.getStatus().name(),
                request.getCreatedAt()
        );
    }

    public CashoutListPageResponse list(UUID userId, String cursor, Integer size, CashoutRequest.Status status) {
        int pageSize = clampPageSize(size);
        Long cursorId = decodeCursor(cursor);

        Specification<CashoutRequest> spec = buildSpecification(userId, cursorId, status);
        List<CashoutRequest> fetched = cashoutRequestRepository.findAll(
                spec, PageRequest.of(0, pageSize + 1, Sort.by(Sort.Direction.DESC, "id"))
        ).getContent();

        boolean hasMore = fetched.size() > pageSize;
        List<CashoutRequest> page = hasMore ? fetched.subList(0, pageSize) : fetched;

        List<CashoutListItemResponse> items = page.stream()
                .map(this::toListItem)
                .collect(Collectors.toList());

        String nextCursor = hasMore ? encodeCursor(page.get(page.size() - 1).getId()) : null;
        return new CashoutListPageResponse(items, nextCursor, hasMore);
    }

    public CashoutListItemResponse getDetail(UUID userId, UUID cashoutId) {
        CashoutRequest request = cashoutRequestRepository.findByPublicIdAndUserId(cashoutId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "CASHOUT_NOT_FOUND"));
        return toListItem(request);
    }

    private CashoutListItemResponse toListItem(CashoutRequest request) {
        Instant completedAt = TERMINAL_STATUSES.contains(request.getStatus()) ? request.getUpdatedAt() : null;
        return new CashoutListItemResponse(
                request.getPublicId(),
                request.getPointAmount(),
                request.getTossPointAmount(),
                request.getStatus().name(),
                request.getCreatedAt(),
                completedAt
        );
    }

    private Specification<CashoutRequest> buildSpecification(UUID userId, Long cursorId, CashoutRequest.Status status) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("user").get("id"), userId));
            if (cursorId != null) {
                predicates.add(cb.lessThan(root.get("id"), cursorId));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private int clampPageSize(Integer size) {
        if (size == null) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
    }

    private String encodeCursor(Long id) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(String.valueOf(id).getBytes(StandardCharsets.UTF_8));
    }

    private Long decodeCursor(String cursor) {
        if (!StringUtils.hasText(cursor)) {
            return null;
        }
        try {
            String decoded = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            return Long.parseLong(decoded);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "COMMON_VALIDATION_ERROR", exception);
        }
    }
}
