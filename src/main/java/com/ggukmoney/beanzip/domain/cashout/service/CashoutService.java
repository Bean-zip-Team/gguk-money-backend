package com.ggukmoney.beanzip.domain.cashout.service;

import com.ggukmoney.beanzip.domain.auth.entity.AuthIdentity;
import com.ggukmoney.beanzip.domain.auth.repository.AuthIdentityRepository;
import com.ggukmoney.beanzip.domain.cashout.client.TossPromotionClient;
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
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
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

    @Value("${app.cashout.toss.promotion-code:}")
    private String promotionCode;

    public CashoutQuoteResponse getQuote(UUID userId) {
        long balance = pointAccountService.getBalance(userId);
        int minimumPoint = cashoutPolicyConfig.minimumPoint();
        double rate = cashoutPolicyConfig.pointToKrwRate();
        long tossPointAmount = (long) Math.floor(balance * rate);
        boolean eligible = balance >= minimumPoint;

        return new CashoutQuoteResponse(
                balance,
                tossPointAmount,
                minimumPoint,
                new CashoutQuoteResponse.RateInfo(rate),
                eligible
        );
    }

    @Transactional
    public CashoutSubmitResponse submit(UUID userId, UUID idempotencyKey) {
        Optional<CashoutRequest> existing = cashoutRequestRepository.findByUserIdAndIdempotencyKey(userId, idempotencyKey);
        if (existing.isPresent()) {
            return toResponse(existing.get());
        }

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
        long tossPointAmount = (long) Math.floor(balance * cashoutPolicyConfig.pointToKrwRate());

        PointAccount account = pointAccountService.debit(userId, balance);
        pointLedgerService.recordDebit(account, user, balance, DEBIT_REASON_CASHOUT, idempotencyKey);

        CashoutRequest request = cashoutRequestRepository.save(
                CashoutRequest.createFor(user, balance, tossPointAmount, idempotencyKey)
        );

        submitToToss(request, userId, user, balance, idempotencyKey);

        return toResponse(cashoutRequestRepository.save(request));
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
        AuthIdentity identity = authIdentityRepository.findByUserIdAndProvider(userId, AuthIdentity.Provider.TOSS)
                .orElse(null);
        if (identity == null) {
            log.error("출금 신청에 연결된 Toss 계정이 없음: cashoutId={}", request.getPublicId());
            request.markFailed();
            reverseDebit(request, user, balance, idempotencyKey);
            return;
        }

        String key;
        try {
            key = tossPromotionClient.getKey(identity.getProviderUserId());
        } catch (RuntimeException exception) {
            log.error("Toss get-key 호출 실패, 환불 처리: cashoutId={}", request.getPublicId(), exception);
            request.markFailed();
            reverseDebit(request, user, balance, idempotencyKey);
            return;
        }

        try {
            TossPromotionClient.PromotionExecutionOutcome outcome =
                    tossPromotionClient.executePromotion(promotionCode, key, request.getTossPointAmount());
            if (outcome.succeeded()) {
                request.markProcessing(key);
            } else {
                log.warn("Toss execute-promotion 명시적 실패, 환불 처리: cashoutId={} errorCode={}",
                        request.getPublicId(), outcome.tossErrorCode());
                request.markFailed();
                reverseDebit(request, user, balance, idempotencyKey);
            }
        } catch (TossPromotionClient.AmbiguousTossFailureException exception) {
            log.error("Toss execute-promotion 결과 불명 — 수동 확인 필요: cashoutId={}", request.getPublicId(), exception);
            // REQUESTED 상태 유지, 자동 환불하지 않음
        }
    }

    /**
     * {@code PROCESSING} 건의 execution-result 폴링 결과를 반영한다. {@code CashoutProcessingScheduler}에서 호출된다.
     */
    @Transactional
    public void finalizeProcessingCashout(CashoutRequest request, TossPromotionClient.PromotionResultStatus result) {
        switch (result) {
            case SUCCESS -> request.markSucceeded();
            case FAILED -> {
                request.markFailed();
                reverseDebit(request, request.getUser(), request.getPointAmount(), request.getIdempotencyKey());
            }
            case PENDING -> {
                // 다음 tick에 재확인
            }
        }
        cashoutRequestRepository.save(request);
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
