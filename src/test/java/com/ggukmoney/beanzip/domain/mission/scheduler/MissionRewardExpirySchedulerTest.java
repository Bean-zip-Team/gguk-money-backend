package com.ggukmoney.beanzip.domain.mission.scheduler;

import com.ggukmoney.beanzip.domain.mission.service.MissionRewardService;
import com.ggukmoney.beanzip.global.scheduler.AdvisoryLockRunner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MissionRewardExpirySchedulerTest {

    private static final Instant NOW = Instant.parse("2026-09-21T15:05:00Z");

    private final MissionRewardService missionRewardService = mock(MissionRewardService.class);
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

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
        when(missionRewardService.expireDueRewards(any()))
                .thenReturn(new MissionRewardService.ExpiryResult(0, 0L));
    }

    @Test
    void runsJustAfterMidnightInBusinessTimeZone() throws Exception {
        Scheduled scheduled = MissionRewardExpiryScheduler.class
                .getMethod("expireUnclaimedRewards")
                .getAnnotation(Scheduled.class);

        // 자정 정각이 아니라 조금 뒤에 돈다. 리셋 순간의 수령 요청과 겹치지 않게 하기 위해서다.
        assertThat(scheduled.cron()).isEqualTo("${app.mission.reward-expiry-cron:0 5 0 * * *}");
        // 만료 시각을 계산하는 businessZoneId 와 같은 프로퍼티를 쓴다. 한쪽만 바뀌면 어긋난다.
        assertThat(scheduled.zone()).isEqualTo("${app.business-time-zone:Asia/Seoul}");
    }

    @Test
    void expiresRewardsWithTheCurrentInstantAndReleasesTheLock() throws Exception {
        when(resultSet.getBoolean(1)).thenReturn(true);

        scheduler(true).expireUnclaimedRewards();

        verify(missionRewardService).expireDueRewards(NOW);
        verify(release).execute();
    }

    @Test
    void staysIdleWhenAnotherInstanceHoldsTheLock() throws Exception {
        // 블루그린 배포 중에는 인스턴스가 겹친다. 두 곳에서 같은 보상을 마감하면 로그가 두 배로 불어난다.
        when(resultSet.getBoolean(1)).thenReturn(false);

        scheduler(true).expireUnclaimedRewards();

        verify(missionRewardService, never()).expireDueRewards(any());
        verify(release, never()).execute();
    }

    @Test
    void doesNothingWhenTheKillSwitchIsOff() throws Exception {
        scheduler(false).expireUnclaimedRewards();

        // 소멸은 되돌릴 수 없다. 잘못 돌 때 재배포 없이 멈출 수단이 있어야 한다.
        verify(missionRewardService, never()).expireDueRewards(any());
        verify(dataSource, never()).getConnection();
    }

    private MissionRewardExpiryScheduler scheduler(boolean enabled) {
        return new MissionRewardExpiryScheduler(
                missionRewardService, new AdvisoryLockRunner(dataSource), clock, enabled);
    }
}
