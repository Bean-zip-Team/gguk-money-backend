package com.ggukmoney.beanzip.global.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.task.ThreadPoolTaskSchedulerCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 스케줄 작업이 쓰는 스레드 수.
 *
 * <p>설정하지 않으면 스프링 부트가 <b>스레드 하나</b>로 모든 스케줄 작업을 돌린다. 그 상태에서는
 * 오래 걸리는 작업 하나가 나머지 전부를 밀어 버린다. 키캡 박스 알림은 1분마다 돌고, 데일리 미션
 * 알림은 밤 9시에 전체 유저를 훑으며 유저마다 토스 API 를 동기로 호출한다. 풀이 하나면 그 배치가
 * 도는 동안 키캡 알림이 한 통도 나가지 않는다.
 *
 * <p>스케줄러 빈을 직접 만들지 않고 커스터마이저로 값만 바꾸는 이유는, 부트의 자동 구성을 그대로
 * 두기 위해서다. 빈을 직접 만들면 자동 구성이 물러나면서 {@code spring.task.scheduling.shutdown.*}
 * 과 {@code thread-name-prefix} 가 <b>경고도 없이</b> 무시되고, 가상 스레드 경로도 함께 사라진다.
 * 종료 처리도 부트 기본값을 그대로 쓴다 — 컨텍스트가 닫히는 즉시 새 작업 제출을 막는 쪽이,
 * 돌던 작업을 기다리다 그 사이에 다른 배치가 새로 뜨는 것보다 안전하다.
 *
 * <p>이 값을 올릴 때는 DB 커넥션 풀도 함께 봐야 한다. 배치는 어드바이저리 락을 잡는 동안 커넥션을
 * 하나씩 붙들고 있으므로, 동시에 도는 배치 수만큼 웹 요청이 쓸 커넥션이 줄어든다.
 */
@Slf4j
@Configuration
public class SchedulingConfig {

    /**
     * 스레드 수를 코드에 두는 이유는, 운영 설정에서 빠뜨려도 하나로 떨어지지 않게 하기 위해서다.
     * {@code spring.task.scheduling.pool.size} 를 설정하면 그 값이 그대로 쓰인다.
     */
    @Bean
    public ThreadPoolTaskSchedulerCustomizer schedulingPoolSizeCustomizer(
            @Value("${spring.task.scheduling.pool.size:5}") int poolSize
    ) {
        log.info("SCHEDULER_POOL_CONFIGURED poolSize={}", poolSize);
        return scheduler -> scheduler.setPoolSize(poolSize);
    }
}
