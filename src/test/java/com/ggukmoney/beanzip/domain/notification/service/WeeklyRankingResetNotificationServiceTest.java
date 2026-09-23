package com.ggukmoney.beanzip.domain.notification.service;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.ggukmoney.beanzip.domain.notification.entity.WeeklyRankingResetNotificationBatch;
import com.ggukmoney.beanzip.domain.notification.repository.NotificationPreferenceRepository;
import com.ggukmoney.beanzip.domain.notification.repository.WeeklyRankingResetNotificationBatchRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class WeeklyRankingResetNotificationServiceTest {

    private final WeeklyRankingResetNotificationBatchRepository batchRepository =
            mock(WeeklyRankingResetNotificationBatchRepository.class);
    private final NotificationPreferenceRepository preferenceRepository = mock(NotificationPreferenceRepository.class);
    private final NotificationDeliveryPersistenceService persistenceService =
            mock(NotificationDeliveryPersistenceService.class);
    private final Instant now = Instant.parse("2026-07-26T15:10:00Z");
    private final WeeklyRankingResetNotificationService service = new WeeklyRankingResetNotificationService(
            batchRepository,
            preferenceRepository,
            persistenceService,
            Clock.fixed(now, ZoneOffset.UTC)
    );

    @BeforeEach
    void setUp() {
        when(persistenceService.isWeeklyResetCampaignConfigured()).thenReturn(true);
    }

    @Test
    void enqueuesSendableFinalRankCandidatesAndAdvancesTheCursor() {
        long seasonId = 11L;
        WeeklyRankingResetNotificationBatch batch = batch(seasonId);
        UUID userId = UUID.randomUUID();
        NotificationPreferenceRepository.SendableCandidate candidate = candidate(42L, userId);
        when(batchRepository.findFirstByEnqueueCompletedFalseAndCompletedFalseOrderByIdAsc())
                .thenReturn(Optional.of(batch));
        when(preferenceRepository.findWeeklyResetCandidates(eq(seasonId), eq(0L), eq(PageRequest.of(0, 100))))
                .thenReturn(List.of(candidate));
        when(persistenceService.enqueueWeeklyReset(userId, 90L, seasonId)).thenReturn(1);
        when(batchRepository.saveAndFlush(batch)).thenReturn(batch);

        assertThat(service.enqueueNextPreferencePage()).isEqualTo(1);

        assertThat(batch.getPreferenceCursor()).isEqualTo(42L);
        assertThat(batch.isEnqueueCompleted()).isTrue();
        verify(persistenceService).enqueueWeeklyReset(userId, 90L, seasonId);
    }

    @Test
    void emptyCandidatePageMarksEnqueueCompleteWithoutMovingCursor() {
        WeeklyRankingResetNotificationBatch batch = batch(12L);
        when(batchRepository.findFirstByEnqueueCompletedFalseAndCompletedFalseOrderByIdAsc())
                .thenReturn(Optional.of(batch));
        when(preferenceRepository.findWeeklyResetCandidates(eq(12L), eq(0L), any(PageRequest.class)))
                .thenReturn(List.of());

        assertThat(service.enqueueNextPreferencePage()).isZero();

        assertThat(batch.getPreferenceCursor()).isZero();
        assertThat(batch.isEnqueueCompleted()).isTrue();
        verify(batchRepository).saveAndFlush(batch);
    }

    @Test
    void campaignNotConfiguredLeavesTheCursorPendingForAConfiguredRetry() {
        WeeklyRankingResetNotificationBatch batch = batch(13L);
        when(batchRepository.findFirstByEnqueueCompletedFalseAndCompletedFalseOrderByIdAsc())
                .thenReturn(Optional.of(batch));
        when(persistenceService.isWeeklyResetCampaignConfigured()).thenReturn(false);

        assertThat(service.enqueueNextPreferencePage()).isZero();

        assertThat(batch.getPreferenceCursor()).isZero();
        assertThat(batch.isEnqueueCompleted()).isFalse();
        verifyNoInteractions(preferenceRepository);
    }

    @Test
    void pageProgressLogReportsTheCursorBeforeAndAfterAdvancing() {
        long seasonId = 14L;
        WeeklyRankingResetNotificationBatch batch = batch(seasonId);
        UUID userId = UUID.randomUUID();
        NotificationPreferenceRepository.SendableCandidate candidate = candidate(42L, userId);
        when(batchRepository.findFirstByEnqueueCompletedFalseAndCompletedFalseOrderByIdAsc())
                .thenReturn(Optional.of(batch));
        when(preferenceRepository.findWeeklyResetCandidates(eq(seasonId), eq(0L), eq(PageRequest.of(0, 100))))
                .thenReturn(List.of(candidate));
        when(persistenceService.enqueueWeeklyReset(userId, 90L, seasonId)).thenReturn(1);

        Logger logger = (Logger) LoggerFactory.getLogger(WeeklyRankingResetNotificationService.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            service.enqueueNextPreferencePage();

            assertThat(appender.list)
                    .extracting(ILoggingEvent::getFormattedMessage)
                    .anySatisfy(message -> assertThat(message)
                            .contains("fromCursor=0")
                            .contains("toCursor=42"));
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    @Test
    void fullPreferencePageAdvancesCursorWithoutCompletingTheScan() {
        long seasonId = 15L;
        WeeklyRankingResetNotificationBatch batch = batch(seasonId);
        List<NotificationPreferenceRepository.SendableCandidate> candidates = LongStream.rangeClosed(1, 100)
                .mapToObj(id -> candidate(id, UUID.randomUUID()))
                .toList();
        when(batchRepository.findFirstByEnqueueCompletedFalseAndCompletedFalseOrderByIdAsc())
                .thenReturn(Optional.of(batch));
        when(preferenceRepository.findWeeklyResetCandidates(eq(seasonId), eq(0L), eq(PageRequest.of(0, 100))))
                .thenReturn(candidates);

        assertThat(service.enqueueNextPreferencePage()).isZero();

        assertThat(batch.getPreferenceCursor()).isEqualTo(100L);
        assertThat(batch.isEnqueueCompleted()).isFalse();
    }

    @Test
    void enqueueFailureLeavesThePageCursorUnchanged() {
        long seasonId = 16L;
        WeeklyRankingResetNotificationBatch batch = batch(seasonId);
        UUID firstUserId = UUID.randomUUID();
        UUID secondUserId = UUID.randomUUID();
        when(batchRepository.findFirstByEnqueueCompletedFalseAndCompletedFalseOrderByIdAsc())
                .thenReturn(Optional.of(batch));
        when(preferenceRepository.findWeeklyResetCandidates(eq(seasonId), eq(0L), eq(PageRequest.of(0, 100))))
                .thenReturn(List.of(candidate(1L, firstUserId), candidate(2L, secondUserId)));
        when(persistenceService.enqueueWeeklyReset(firstUserId, 90L, seasonId)).thenReturn(1);
        when(persistenceService.enqueueWeeklyReset(secondUserId, 90L, seasonId))
                .thenThrow(new IllegalStateException("database unavailable"));

        assertThatThrownBy(service::enqueueNextPreferencePage)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("database unavailable");

        assertThat(batch.getPreferenceCursor()).isZero();
        assertThat(batch.isEnqueueCompleted()).isFalse();
        verify(batchRepository, never()).saveAndFlush(batch);
    }

    @Test
    void openDeliveriesKeepAnEnqueuedBatchIncomplete() {
        WeeklyRankingResetNotificationBatch batch = batch(17L);
        batch.recordPreferencePage(42L, true, now);
        when(batchRepository.findFirstByEnqueueCompletedTrueAndCompletedFalseOrderByIdAsc())
                .thenReturn(Optional.of(batch));
        when(persistenceService.hasOpenWeeklyResetDeliveries(90L)).thenReturn(true);

        assertThat(service.completeOneDrainedBatch()).isFalse();

        assertThat(batch.isCompleted()).isFalse();
        verify(batchRepository, never()).saveAndFlush(batch);
    }

    @Test
    void terminalDeliveriesAllowTheBatchToComplete() {
        WeeklyRankingResetNotificationBatch batch = batch(18L);
        batch.recordPreferencePage(42L, true, now);
        when(batchRepository.findFirstByEnqueueCompletedTrueAndCompletedFalseOrderByIdAsc())
                .thenReturn(Optional.of(batch));
        when(persistenceService.hasOpenWeeklyResetDeliveries(90L)).thenReturn(false);

        assertThat(service.completeOneDrainedBatch()).isTrue();

        assertThat(batch.isCompleted()).isTrue();
        assertThat(batch.getCompletedAt()).isEqualTo(now);
        verify(batchRepository).saveAndFlush(batch);
    }

    private WeeklyRankingResetNotificationBatch batch(long seasonId) {
        WeeklyRankingResetNotificationBatch batch = WeeklyRankingResetNotificationBatch.start(seasonId, now);
        ReflectionTestUtils.setField(batch, "id", 90L);
        return batch;
    }

    private NotificationPreferenceRepository.SendableCandidate candidate(long preferenceId, UUID userId) {
        return new NotificationPreferenceRepository.SendableCandidate() {
            @Override
            public Long getPreferenceId() {
                return preferenceId;
            }

            @Override
            public UUID getUserId() {
                return userId;
            }
        };
    }
}
