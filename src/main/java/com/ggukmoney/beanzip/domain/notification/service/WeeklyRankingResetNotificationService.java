package com.ggukmoney.beanzip.domain.notification.service;

import com.ggukmoney.beanzip.domain.notification.entity.WeeklyRankingResetNotificationBatch;
import com.ggukmoney.beanzip.domain.notification.repository.NotificationPreferenceRepository;
import com.ggukmoney.beanzip.domain.notification.repository.WeeklyRankingResetNotificationBatchRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class WeeklyRankingResetNotificationService {

    static final int PAGE_SIZE = 100;

    private final WeeklyRankingResetNotificationBatchRepository batchRepository;
    private final NotificationPreferenceRepository preferenceRepository;
    private final NotificationDeliveryPersistenceService persistenceService;
    private final Clock clock;

    @Transactional
    public int enqueueNextPreferencePage() {
        WeeklyRankingResetNotificationBatch batch = batchRepository
                .findFirstByEnqueueCompletedFalseAndCompletedFalseOrderByIdAsc()
                .orElse(null);
        if (batch == null) {
            return 0;
        }
        if (!persistenceService.isWeeklyResetCampaignConfigured()) {
            log.warn("Weekly rank reset recipient scan paused because the RANK_CHANGE campaign is not configured. "
                    + "seasonId={}, cursor={}", batch.getSeasonId(), batch.getPreferenceCursor());
            return 0;
        }

        long previousCursor = batch.getPreferenceCursor();
        List<NotificationPreferenceRepository.SendableCandidate> candidates =
                preferenceRepository.findWeeklyResetCandidates(
                        batch.getSeasonId(), previousCursor, PageRequest.of(0, PAGE_SIZE));
        if (candidates.isEmpty()) {
            batch.markEnqueueCompleted(clock.instant());
            batchRepository.saveAndFlush(batch);
            log.info("Weekly rank reset recipient scan completed. seasonId={}, cursor={}",
                    batch.getSeasonId(), batch.getPreferenceCursor());
            return 0;
        }

        int enqueued = 0;
        for (NotificationPreferenceRepository.SendableCandidate candidate : candidates) {
            enqueued += persistenceService.enqueueWeeklyReset(candidate.getUserId(), batch.getId(), batch.getSeasonId());
        }
        long lastPreferenceId = candidates.getLast().getPreferenceId();
        batch.recordPreferencePage(lastPreferenceId, candidates.size() < PAGE_SIZE, clock.instant());
        batchRepository.saveAndFlush(batch);
        log.info("Weekly rank reset recipient page processed. seasonId={}, fromCursor={}, toCursor={}, "
                        + "candidateCount={}, deliveryCount={}, enqueueCompleted={}",
                batch.getSeasonId(), previousCursor, lastPreferenceId, candidates.size(), enqueued,
                batch.isEnqueueCompleted());
        return enqueued;
    }

    @Transactional
    public boolean completeOneDrainedBatch() {
        WeeklyRankingResetNotificationBatch batch = batchRepository
                .findFirstByEnqueueCompletedTrueAndCompletedFalseOrderByIdAsc()
                .orElse(null);
        if (batch == null || persistenceService.hasOpenWeeklyResetDeliveries(batch.getId())) {
            return false;
        }
        batch.markCompleted(clock.instant());
        batchRepository.saveAndFlush(batch);
        log.info("Weekly rank reset notification batch completed. seasonId={}, batchId={}",
                batch.getSeasonId(), batch.getId());
        return true;
    }
}
