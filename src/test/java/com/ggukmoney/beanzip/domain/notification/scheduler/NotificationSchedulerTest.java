package com.ggukmoney.beanzip.domain.notification.scheduler;

import com.ggukmoney.beanzip.domain.notification.service.NotificationDeliveryService;
import org.junit.jupiter.api.Test;

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

class NotificationSchedulerTest {

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
}
