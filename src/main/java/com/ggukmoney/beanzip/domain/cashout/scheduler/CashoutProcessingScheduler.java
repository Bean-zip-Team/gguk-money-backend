package com.ggukmoney.beanzip.domain.cashout.scheduler;

import com.ggukmoney.beanzip.domain.cashout.client.TossPromotionClient;
import com.ggukmoney.beanzip.domain.cashout.entity.CashoutRequest;
import com.ggukmoney.beanzip.domain.cashout.repository.CashoutRequestRepository;
import com.ggukmoney.beanzip.domain.cashout.service.CashoutService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * PROCESSING 상태인 출금 건의 Toss 지급 결과(execution-result)를 주기적으로 폴링해 확정한다.
 * get-key/execute-promotion은 POST /cashouts 요청 안에서 이미 동기로 처리되므로, 이 스케줄러는
 * "실제로 돈이 들어갔는지" 확인이 지연될 수 있는 부분만 담당한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CashoutProcessingScheduler {

    private final CashoutRequestRepository cashoutRequestRepository;
    private final TossPromotionClient tossPromotionClient;
    private final CashoutService cashoutService;

    @Scheduled(fixedRate = 30_000)
    public void pollProcessingCashouts() {
        List<CashoutRequest> processing =
                cashoutRequestRepository.findByStatusAndTossPromotionKeyIsNotNull(CashoutRequest.Status.PROCESSING);

        for (CashoutRequest request : processing) {
            try {
                TossPromotionClient.PromotionResultStatus result =
                        tossPromotionClient.getExecutionResult(request.getTossPromotionKey());
                cashoutService.finalizeProcessingCashout(request, result);
            } catch (RuntimeException exception) {
                log.error("Toss execution-result 폴링 실패: cashoutId={}", request.getPublicId(), exception);
            }
        }
    }
}
