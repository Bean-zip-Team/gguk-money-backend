package com.ggukmoney.beanzip.global.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.task.TaskSchedulingAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 부트의 자동 구성을 실제로 올려서 확인한다.
 *
 * <p>설정 객체의 필드만 보면 기본값이 되돌아가는 회귀를 잡지 못한다. 이 설정의 핵심이 바로
 * <b>프로퍼티가 없을 때의 기본값</b>이기 때문이다.
 */
class SchedulingConfigTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(TaskSchedulingAutoConfiguration.class))
            // 부트는 스케줄링이 켜져 있을 때만 스케줄러 빈을 만든다. 운영과 같은 조건으로 올린다.
            .withUserConfiguration(SchedulingConfig.class, SchedulingEnabled.class);

    @EnableScheduling
    @Configuration(proxyBeanMethods = false)
    static class SchedulingEnabled {
    }

    @Test
    void runsScheduledWorkOnMoreThanOneThreadWithoutAnyProperty() {
        // 스레드가 하나면 밤 9시 배치가 도는 동안 1분마다 도는 키캡 알림이 통째로 밀린다.
        // 부트 기본값이 1이므로, 아무 설정도 하지 않은 상태를 확인해야 의미가 있다.
        contextRunner.run(context -> assertThat(context.getBean(ThreadPoolTaskScheduler.class)
                .getScheduledThreadPoolExecutor()
                .getCorePoolSize()).isEqualTo(5));
    }

    @Test
    void lettingOperationsOverrideThePoolSize() {
        contextRunner.withPropertyValues("spring.task.scheduling.pool.size=8")
                .run(context -> assertThat(context.getBean(ThreadPoolTaskScheduler.class)
                        .getScheduledThreadPoolExecutor()
                        .getCorePoolSize()).isEqualTo(8));
    }

    @Test
    void blocksNewScheduledWorkAsSoonAsTheContextCloses() {
        // 이 값이 true 면 스프링이 조기 종료 경로를 건너뛴다. 그러면 컨텍스트가 닫히는 동안에도
        // 10초 주기 지급 재시도 같은 작업이 새로 떠서, 닫히는 중인 DataSource 를 물게 된다.
        contextRunner.run(context -> assertThat((Boolean) ReflectionTestUtils.getField(
                context.getBean(ThreadPoolTaskScheduler.class), "waitForTasksToCompleteOnShutdown"))
                .isFalse());
    }

    @Test
    void keepsTheShutdownPropertiesOfTheFrameworkAlive() {
        // 스케줄러 빈을 직접 만들면 이 설정들이 경고도 없이 무시된다. 커스터마이저로 값만 바꾸는
        // 이유가 여기에 있다.
        contextRunner.withPropertyValues(
                        "spring.task.scheduling.shutdown.await-termination=true",
                        "spring.task.scheduling.shutdown.await-termination-period=45s")
                .run(context -> assertThat((Long) ReflectionTestUtils.getField(
                        context.getBean(ThreadPoolTaskScheduler.class), "awaitTerminationMillis"))
                        .isEqualTo(45_000L));
    }
}
