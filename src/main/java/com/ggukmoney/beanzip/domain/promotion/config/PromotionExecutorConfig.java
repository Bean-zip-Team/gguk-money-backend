package com.ggukmoney.beanzip.domain.promotion.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 지급 실행 전용 풀. 개봉 요청 스레드에서 토스를 부르지 않기 위한 것이다.
 *
 * <p>거부 정책은 {@code AbortPolicy} 다. {@code CallerRunsPolicy} 를 쓰면 큐가 찼을 때 개봉
 * 요청 스레드가 토스 왕복을 떠안는다. 거부된 건은 로그만 남기고 스케줄러가 줍는다 — 이 풀은
 * 빠른 경로일 뿐이고 정확성은 스케줄러가 책임진다.
 */
@Configuration
public class PromotionExecutorConfig {

    public static final String EXECUTOR_BEAN = "promotionGrantExecutor";

    @Bean(name = EXECUTOR_BEAN)
    public Executor promotionGrantExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("promotion-grant-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.initialize();
        return executor;
    }
}
