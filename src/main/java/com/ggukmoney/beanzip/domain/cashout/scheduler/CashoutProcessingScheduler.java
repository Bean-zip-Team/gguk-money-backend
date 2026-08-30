package com.ggukmoney.beanzip.domain.cashout.scheduler;

import com.ggukmoney.beanzip.domain.auth.entity.AuthIdentity;
import com.ggukmoney.beanzip.domain.auth.repository.AuthIdentityRepository;
import com.ggukmoney.beanzip.domain.cashout.client.TossPromotionClient;
import com.ggukmoney.beanzip.domain.cashout.entity.CashoutRequest;
import com.ggukmoney.beanzip.domain.cashout.repository.CashoutRequestRepository;
import com.ggukmoney.beanzip.domain.cashout.service.CashoutService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * PROCESSING 상태인 출금 건의 Toss 지급 결과(execution-result)를 주기적으로 폴링해 확정한다.
 * get-key/execute-promotion은 POST /cashouts 요청 안에서 이미 동기로 처리되므로, 이 스케줄러는
 * "실제로 돈이 들어갔는지" 확인이 지연될 수 있는 부분만 담당한다.
 */
@Slf4j
@Component
public class CashoutProcessingScheduler {

    private static final int MAX_BATCH_SIZE = 100;

    private final CashoutRequestRepository cashoutRequestRepository;
    private final AuthIdentityRepository authIdentityRepository;
    private final TossPromotionClient tossPromotionClient;
    private final CashoutService cashoutService;
    private final int batchSize;
    private final String promotionCode;

    private long lastProcessedId;

    public CashoutProcessingScheduler(
            CashoutRequestRepository cashoutRequestRepository,
            AuthIdentityRepository authIdentityRepository,
            TossPromotionClient tossPromotionClient,
            CashoutService cashoutService,
            @Value("${app.cashout.polling.batch-size:100}") int batchSize,
            @Value("${app.cashout.toss.promotion-code:}") String promotionCode
    ) {
        if (batchSize < 1 || batchSize > MAX_BATCH_SIZE) {
            throw new IllegalArgumentException("batchSize must be between 1 and " + MAX_BATCH_SIZE);
        }
        this.cashoutRequestRepository = cashoutRequestRepository;
        this.authIdentityRepository = authIdentityRepository;
        this.tossPromotionClient = tossPromotionClient;
        this.cashoutService = cashoutService;
        this.batchSize = batchSize;
        this.promotionCode = promotionCode;
    }

    @Scheduled(fixedDelayString = "${app.cashout.polling.fixed-delay:30s}")
    public void pollProcessingCashouts() {
        long tickStart = System.currentTimeMillis();
        long cursorBefore = lastProcessedId;
        boolean wrapped = false;
        List<CashoutRequest> processing = findNextPage(lastProcessedId);
        if (processing.isEmpty() && lastProcessedId > 0) {
            lastProcessedId = 0;
            wrapped = true;
            processing = findNextPage(lastProcessedId);
        }
        Map<UUID, String> tossUserKeys = findTossUserKeys(processing);
        log.info("Cashout polling tick 시작: count={} cursorBefore={} wrapped={}",
                processing.size(), cursorBefore, wrapped);

        for (CashoutRequest request : processing) {
            try {
                UUID userId = request.getUser().getId();
                String tossUserKey = tossUserKeys.get(userId);
                if (!StringUtils.hasText(tossUserKey)) {
                    throw new IllegalStateException(
                            "출금 신청에 연결된 Toss 계정이 없음: cashoutId=" + request.getPublicId());
                }
                long callStart = System.currentTimeMillis();
                TossPromotionClient.PromotionResultStatus result =
                        tossPromotionClient.getExecutionResult(tossUserKey, promotionCode, request.getTossPromotionKey());
                log.info("Cashout execution-result 폴링 결과: cashoutId={} result={} callElapsedMs={}",
                        request.getPublicId(), result, System.currentTimeMillis() - callStart);
                cashoutService.finalizeProcessingCashout(request, result);
            } catch (RuntimeException exception) {
                log.error("Toss execution-result 폴링 실패: cashoutId={}", request.getPublicId(), exception);
            } finally {
                lastProcessedId = request.getId();
            }
        }

        log.info("Cashout polling tick 종료: count={} cursorAfter={} wrapped={} tickElapsedMs={}",
                processing.size(), lastProcessedId, wrapped, System.currentTimeMillis() - tickStart);
    }

    private List<CashoutRequest> findNextPage(long cursor) {
        return cashoutRequestRepository.findProcessingAfterId(
                CashoutRequest.Status.PROCESSING,
                cursor,
                PageRequest.of(0, batchSize)
        );
    }

    private Map<UUID, String> findTossUserKeys(List<CashoutRequest> requests) {
        if (requests.isEmpty()) {
            return Map.of();
        }
        Set<UUID> userIds = requests.stream()
                .map(request -> request.getUser().getId())
                .collect(Collectors.toUnmodifiableSet());
        return authIdentityRepository.findProviderIdentitiesByUserIds(userIds, AuthIdentity.Provider.TOSS).stream()
                .collect(Collectors.toUnmodifiableMap(
                        AuthIdentityRepository.UserProviderIdentity::getUserId,
                        AuthIdentityRepository.UserProviderIdentity::getProviderUserId
                ));
    }
}
