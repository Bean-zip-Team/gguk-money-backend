package com.ggukmoney.beanzip.domain.promotion.event;

import com.ggukmoney.beanzip.domain.promotion.config.PromotionExecutorConfig;
import com.ggukmoney.beanzip.domain.promotion.service.PromotionExecutionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

/**
 * 커밋 직후 지급을 시작한다.
 *
 * <p>여기서 토스를 직접 부르지 않는다. 상자 개봉은 이 앱에서 가장 뜨거운 API 중 하나인데,
 * get-key 와 execute 왕복 두 번을 요청 스레드에서 하면 그만큼 느려진다.
 *
 * <p>본문 전체를 삼킨다. AFTER_COMMIT 예외는 커밋을 되돌리지는 않지만 호출자에게 전파돼
 * 개봉 API 를 깨뜨린다. 여기서 아무것도 못 해도 스케줄러가 같은 행을 집는다.
 *
 * <p>생성자를 직접 쓰는 이유는 Lombok 이 필드의 {@code @Qualifier} 를 생성자 파라미터로
 * 복사하지 않기 때문이다. Spring Boot 가 {@code applicationTaskExecutor} 를 자동 등록하므로
 * 한정자 없이는 주입 대상이 모호해진다.
 */
@Component
public class PromotionGrantCreatedListener {

    private static final Logger log = LoggerFactory.getLogger(PromotionGrantCreatedListener.class);

    private final PromotionExecutionService promotionExecutionService;
    private final Executor promotionGrantExecutor;

    public PromotionGrantCreatedListener(
            PromotionExecutionService promotionExecutionService,
            @Qualifier(PromotionExecutorConfig.EXECUTOR_BEAN) Executor promotionGrantExecutor
    ) {
        this.promotionExecutionService = promotionExecutionService;
        this.promotionGrantExecutor = promotionGrantExecutor;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onGrantCreated(PromotionGrantCreatedEvent event) {
        try {
            promotionGrantExecutor.execute(() -> {
                try {
                    promotionExecutionService.execute(event.grantId());
                } catch (RuntimeException exception) {
                    log.error("Promotion grant execution failed; grantId={}", event.grantId(), exception);
                }
            });
        } catch (RejectedExecutionException exception) {
            log.warn("Promotion executor rejected; scheduler will pick it up. grantId={}", event.grantId());
        } catch (RuntimeException exception) {
            log.error("Failed to dispatch promotion grant; grantId={}", event.grantId(), exception);
        }
    }
}
