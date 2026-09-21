package com.ggukmoney.beanzip.global.config;

import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class SchedulingConfigTest {

    private final SchedulingConfig config = new SchedulingConfig();

    @Test
    void runsScheduledWorkOnMoreThanOneThread() {
        ThreadPoolTaskScheduler scheduler = config.taskScheduler(5);

        // 스레드가 하나면 밤 9시 배치가 도는 동안 1분마다 도는 키캡 알림이 통째로 밀린다.
        assertThat(scheduler.getPoolSize()).isGreaterThan(1);
        scheduler.shutdown();
    }

    @Test
    void doesNotHoldShutdownUntilALongBatchFinishes() {
        ThreadPoolTaskScheduler scheduler = config.taskScheduler(5);

        // 돌고 있던 작업은 마무리할 시간을 주되 배포를 막지는 않는다. 중간에 끊겨도 어드바이저리 락은
        // 커넥션이 닫히면서 풀린다.
        assertThat((Long) ReflectionTestUtils.getField(scheduler, "awaitTerminationMillis"))
                .isBetween(1L, 60_000L);
        scheduler.shutdown();
    }
}
