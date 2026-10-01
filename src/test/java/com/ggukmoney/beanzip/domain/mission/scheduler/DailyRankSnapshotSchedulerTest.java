package com.ggukmoney.beanzip.domain.mission.scheduler;

import com.ggukmoney.beanzip.domain.mission.service.DailyRankSnapshotService;
import com.ggukmoney.beanzip.global.scheduler.AdvisoryLockRunner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DailyRankSnapshotSchedulerTest {

    private final DailyRankSnapshotService dailyRankSnapshotService = mock(DailyRankSnapshotService.class);

    private final DataSource dataSource = mock(DataSource.class);
    private final Connection connection = mock(Connection.class);
    private final PreparedStatement acquire = mock(PreparedStatement.class);
    private final PreparedStatement release = mock(PreparedStatement.class);
    private final ResultSet resultSet = mock(ResultSet.class);

    @BeforeEach
    void stubLockConnection() throws Exception {
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(acquire, release);
        when(acquire.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true);
        when(dailyRankSnapshotService.captureToday())
                .thenReturn(new DailyRankSnapshotService.SnapshotResult(LocalDate.of(2026, 9, 21), 7L, 12, 3));
    }

    @Test
    void runsJustBeforeMidnightInBusinessTimeZone() throws Exception {
        Scheduled scheduled = DailyRankSnapshotScheduler.class
                .getMethod("captureDailyRankSnapshot")
                .getAnnotation(Scheduled.class);

        // 자정 직후에 찍으면 주간 시즌이 초기화된 뒤라 전원이 0점인 순서가 기준이 된다.
        assertThat(scheduled.cron()).isEqualTo("${app.mission.rank-snapshot-cron:0 55 23 * * *}");
        assertThat(scheduled.zone()).isEqualTo("${app.business-time-zone:Asia/Seoul}");
    }

    @Test
    void capturesTheSnapshotAndReleasesTheLock() throws Exception {
        when(resultSet.getBoolean(1)).thenReturn(true);

        scheduler(true).captureDailyRankSnapshot();

        verify(dailyRankSnapshotService).captureToday();
        verify(release).execute();
    }

    @Test
    void staysIdleWhenAnotherInstanceHoldsTheLock() throws Exception {
        when(resultSet.getBoolean(1)).thenReturn(false);

        scheduler(true).captureDailyRankSnapshot();

        verify(dailyRankSnapshotService, never()).captureToday();
    }

    @Test
    void doesNothingWhenTheKillSwitchIsOff() throws Exception {
        scheduler(false).captureDailyRankSnapshot();

        verify(dailyRankSnapshotService, never()).captureToday();
        verify(dataSource, never()).getConnection();
    }

    private DailyRankSnapshotScheduler scheduler(boolean enabled) {
        return new DailyRankSnapshotScheduler(dailyRankSnapshotService, new AdvisoryLockRunner(dataSource), enabled);
    }
}
