package com.ggukmoney.beanzip.global.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * 스케줄 작업이 쓰는 스레드 풀.
 *
 * <p>설정하지 않으면 스프링이 <b>스레드 하나</b>로 모든 스케줄 작업을 돌린다. 그 상태에서는 오래
 * 걸리는 작업 하나가 나머지 전부를 밀어 버린다. 키캡 박스 알림은 1분마다 돌고, 데일리 미션 알림은
 * 밤 9시에 전체 유저를 훑으며 유저마다 토스 API 를 동기로 호출한다. 풀이 하나면 그 배치가 도는
 * 동안 키캡 알림이 한 통도 나가지 않는다.
 *
 * <p>스레드 수를 코드에 두는 이유는, 운영 설정에서 빠뜨려도 하나로 떨어지지 않게 하기 위해서다.
 * {@code spring.task.scheduling.pool.size} 를 설정하면 그 값이 그대로 쓰인다.
 *
 * <p>이 값을 올릴 때는 DB 커넥션 풀도 함께 봐야 한다. 배치는 어드바이저리 락을 잡는 동안 커넥션을
 * 하나씩 붙들고 있으므로, 동시에 도는 배치 수만큼 웹 요청이 쓸 커넥션이 줄어든다.
 */
@Slf4j
@Configuration
public class SchedulingConfig {

    @Bean
    public ThreadPoolTaskScheduler taskScheduler(
            @Value("${spring.task.scheduling.pool.size:5}") int poolSize
    ) {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(poolSize);
        scheduler.setThreadNamePrefix("scheduling-");
        // 종료 시점에 돌고 있던 작업은 마무리할 시간을 준다. 다만 밤 9시 배치처럼 오래 걸리는 작업을
        // 끝까지 기다리면 배포가 멈추므로 30초에서 끊는다. 중간에 끊겨도 어드바이저리 락은 커넥션이
        // 닫히면서 풀리고, 다음 실행이 같은 지점부터 다시 훑는다.
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(30);
        log.info("SCHEDULER_POOL_CONFIGURED poolSize={}", poolSize);
        return scheduler;
    }
}
