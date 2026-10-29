package com.ggukmoney.beanzip.domain.abuse.scheduler;

import com.ggukmoney.beanzip.domain.abuse.client.DiscordWebhookClient;
import com.ggukmoney.beanzip.domain.abuse.service.AbuseDetectionService;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AbuseDetectionSchedulerTest {

    // 2026-10-24 14:05:07 KST
    private static final Instant NOW = Instant.parse("2026-10-24T05:05:07Z");

    private final AbuseDetectionService abuseDetectionService = mock(AbuseDetectionService.class);
    private final DiscordWebhookClient discordWebhookClient = mock(DiscordWebhookClient.class);
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
        when(resultSet.getBoolean(1)).thenReturn(true);
        when(abuseDetectionService.detect(any(), any()))
                .thenReturn(new AbuseDetectionService.Result(List.of(), "알림"));
    }

    @Test
    void runsHourlyJustAfterTheHourInBusinessTimeZone() throws Exception {
        Scheduled scheduled = AbuseDetectionScheduler.class
                .getMethod("detectLastHour")
                .getAnnotation(Scheduled.class);

        assertThat(scheduled.cron()).isEqualTo("${app.abuse.detection-cron:0 5 * * * *}");
        assertThat(scheduled.zone()).isEqualTo("${app.business-time-zone:Asia/Seoul}");
    }

    @Test
    void checksThePreviousFullHourAndSendsTheAlert() {
        scheduler(true).detectLastHour();

        // 14:05 KST 실행이면 13:00~14:00 KST 를 본다.
        verify(abuseDetectionService).detect(
                Instant.parse("2026-10-24T04:00:00Z"),
                Instant.parse("2026-10-24T05:00:00Z"));
        verify(discordWebhookClient).send("알림");
    }

    @Test
    void staysIdleWhenDisabled() {
        scheduler(false).detectLastHour();

        verify(abuseDetectionService, never()).detect(any(), any());
        verify(discordWebhookClient, never()).send(any());
    }

    private AbuseDetectionScheduler scheduler(boolean enabled) {
        return new AbuseDetectionScheduler(
                abuseDetectionService,
                discordWebhookClient,
                new AdvisoryLockRunner(dataSource),
                Clock.fixed(NOW, ZoneOffset.UTC),
                enabled
        );
    }
}
