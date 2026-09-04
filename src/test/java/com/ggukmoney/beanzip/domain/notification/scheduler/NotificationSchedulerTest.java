package com.ggukmoney.beanzip.domain.notification.scheduler;

import com.ggukmoney.beanzip.domain.notification.service.NotificationDeliveryService;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThat;

class NotificationSchedulerTest {

    @Test
    void keycapScheduleUsesConfigurableEveryMinuteCronInKst() throws Exception {
        Scheduled scheduled = NotificationScheduler.class
                .getMethod("scheduleKeycapBoxOpenAvailableNotifications")
                .getAnnotation(Scheduled.class);

        assertThat(scheduled.cron()).isEqualTo("${app.smart-message.schedule.keycap-box-cron:0 * * * * *}");
        assertThat(scheduled.zone()).isEqualTo("${app.smart-message.schedule.zone:Asia/Seoul}");
    }

    @Test
    void doesNotInvokeMorningDeliveryWhenAdvisoryLockIsNotAcquired() throws Exception {
        NotificationDeliveryService deliveryService = mock(NotificationDeliveryService.class);
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        PreparedStatement acquire = mock(PreparedStatement.class);
        PreparedStatement release = mock(PreparedStatement.class);
        ResultSet resultSet = mock(ResultSet.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement("SELECT pg_try_advisory_lock(?)")).thenReturn(acquire);
        when(connection.prepareStatement("SELECT pg_advisory_unlock(?)")).thenReturn(release);
        when(acquire.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true);
        when(resultSet.getBoolean(1)).thenReturn(false);

        NotificationScheduler scheduler = new NotificationScheduler(
                deliveryService,
                dataSource,
                Clock.fixed(Instant.parse("2026-07-25T00:00:00Z"), ZoneOffset.UTC)
        );

        scheduler.scheduleMorningNotifications();

        verify(deliveryService, never()).sendMorningNotifications(org.mockito.ArgumentMatchers.any());
        verify(release, never()).execute();
    }

    @Test
    void doesNotInvokeKeycapDeliveryWhenAdvisoryLockIsNotAcquired() throws Exception {
        NotificationDeliveryService deliveryService = mock(NotificationDeliveryService.class);
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        PreparedStatement acquire = mock(PreparedStatement.class);
        PreparedStatement release = mock(PreparedStatement.class);
        ResultSet resultSet = mock(ResultSet.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement("SELECT pg_try_advisory_lock(?)")).thenReturn(acquire);
        when(connection.prepareStatement("SELECT pg_advisory_unlock(?)")).thenReturn(release);
        when(acquire.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true);
        when(resultSet.getBoolean(1)).thenReturn(false);

        NotificationScheduler scheduler = new NotificationScheduler(
                deliveryService,
                dataSource,
                Clock.fixed(Instant.parse("2026-08-03T01:01:00Z"), ZoneOffset.UTC)
        );

        scheduler.scheduleKeycapBoxOpenAvailableNotifications();

        verify(deliveryService, never()).sendKeycapBoxOpenAvailableNotifications(org.mockito.ArgumentMatchers.any());
        verify(release, never()).execute();
    }

    @Test
    void invokesKeycapDeliveryAndReleasesAdvisoryLock() throws Exception {
        NotificationDeliveryService deliveryService = mock(NotificationDeliveryService.class);
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        PreparedStatement acquire = mock(PreparedStatement.class);
        PreparedStatement release = mock(PreparedStatement.class);
        ResultSet resultSet = mock(ResultSet.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement("SELECT pg_try_advisory_lock(?)")).thenReturn(acquire);
        when(connection.prepareStatement("SELECT pg_advisory_unlock(?)")).thenReturn(release);
        when(acquire.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true);
        when(resultSet.getBoolean(1)).thenReturn(true);
        Instant now = Instant.parse("2026-08-03T01:01:00Z");
        NotificationScheduler scheduler = new NotificationScheduler(
                deliveryService,
                dataSource,
                Clock.fixed(now, ZoneOffset.UTC)
        );

        scheduler.scheduleKeycapBoxOpenAvailableNotifications();

        verify(deliveryService).sendKeycapBoxOpenAvailableNotifications(now);
        verify(release).execute();
    }
}
