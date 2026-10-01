package com.ggukmoney.beanzip.global.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

/**
 * 같은 작업이 인스턴스 두 곳에서 동시에 돌지 않게 막는다.
 *
 * <p>블루그린 배포 중에는 인스턴스가 겹치므로 스케줄러가 두 번 실행될 수 있다. PostgreSQL 세션
 * 어드바이저리 락을 잡아 한 쪽만 일하게 한다. 락을 잡지 못하면 조용히 넘어간다 — 다른 인스턴스가
 * 이미 같은 일을 하고 있다는 뜻이다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdvisoryLockRunner {

    private final DataSource dataSource;

    public void runExclusively(long lockKey, Runnable task) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement acquire = connection.prepareStatement("SELECT pg_try_advisory_lock(?)");
             PreparedStatement release = connection.prepareStatement("SELECT pg_advisory_unlock(?)")) {
            acquire.setLong(1, lockKey);
            try (ResultSet resultSet = acquire.executeQuery()) {
                if (!resultSet.next() || !resultSet.getBoolean(1)) {
                    return;
                }
            }
            try {
                task.run();
            } finally {
                release.setLong(1, lockKey);
                release.execute();
            }
        } catch (Exception exception) {
            log.error("ADVISORY_LOCK_TASK_FAILED lockKey={}", lockKey, exception);
        }
    }
}
