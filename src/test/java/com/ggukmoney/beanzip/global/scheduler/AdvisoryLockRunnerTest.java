package com.ggukmoney.beanzip.global.scheduler;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdvisoryLockRunnerTest {

    private final DataSource dataSource = mock(DataSource.class);
    private final Connection connection = mock(Connection.class);
    private final PreparedStatement acquire = mock(PreparedStatement.class);
    private final PreparedStatement release = mock(PreparedStatement.class);
    private final ResultSet resultSet = mock(ResultSet.class);

    private final AdvisoryLockRunner runner = new AdvisoryLockRunner(dataSource);

    @BeforeEach
    void stubConnection() throws Exception {
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(acquire, release);
        when(acquire.executeQuery()).thenReturn(resultSet);
    }

    @Test
    void runsTheTaskAndReleasesTheLock() throws Exception {
        givenLockAcquired(true);
        AtomicBoolean ran = new AtomicBoolean();

        runner.runExclusively(1L, () -> ran.set(true));

        assertThat(ran).isTrue();
        verify(release).execute();
    }

    @Test
    void skipsTheTaskWhenAnotherInstanceHoldsTheLock() throws Exception {
        givenLockAcquired(false);
        AtomicBoolean ran = new AtomicBoolean();

        runner.runExclusively(1L, () -> ran.set(true));

        assertThat(ran).isFalse();
        // 잡지도 못한 락을 푸는 요청을 보내면 남의 락을 건드리는 셈이다.
        verify(release, never()).execute();
    }

    @Test
    void releasesTheLockEvenWhenTheTaskBlowsUp() throws Exception {
        givenLockAcquired(true);

        // 풀지 못하면 세션 락이 남아 다음 실행이 영영 스킵된다. 실패보다 이쪽이 더 오래 간다.
        assertThatCode(() -> runner.runExclusively(1L, () -> {
            throw new IllegalStateException("task failed");
        })).doesNotThrowAnyException();
        verify(release).execute();
    }

    @Test
    void skipsTheTaskWhenTheLockQueryReturnsNothing() throws Exception {
        when(resultSet.next()).thenReturn(false);
        AtomicBoolean ran = new AtomicBoolean();

        runner.runExclusively(1L, () -> ran.set(true));

        assertThat(ran).isFalse();
        verify(release, never()).execute();
    }

    private void givenLockAcquired(boolean acquired) throws Exception {
        when(resultSet.next()).thenReturn(true);
        when(resultSet.getBoolean(1)).thenReturn(acquired);
    }
}
