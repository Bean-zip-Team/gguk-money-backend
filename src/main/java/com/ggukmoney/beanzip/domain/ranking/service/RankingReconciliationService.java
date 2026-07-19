package com.ggukmoney.beanzip.domain.ranking.service;

import com.ggukmoney.beanzip.domain.ranking.entity.RankingEntry;
import com.ggukmoney.beanzip.domain.ranking.entity.RankingSeason;
import com.ggukmoney.beanzip.domain.ranking.redis.RankingRedisMeta;
import com.ggukmoney.beanzip.domain.ranking.redis.RankingRedisRepository;
import com.ggukmoney.beanzip.domain.ranking.repository.RankingEntryRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RankingReconciliationService {

    private static final Logger log = LoggerFactory.getLogger(RankingReconciliationService.class);

    private final RankingSeasonService seasonService;
    private final RankingEntryRepository entryRepository;
    private final RankingRedisRepository redisRepository;
    private final RankingRebuildService rebuildService;
    private final RankingProperties properties;
    private final Clock clock;

    @Transactional(readOnly = true)
    public void reconcileActiveAllTime() {
        seasonService.findActiveAllTimeSeason().ifPresent(this::reconcile);
    }

    private void reconcile(RankingSeason season) {
        Optional<RankingRedisMeta> currentMeta = redisRepository.findMeta(season.getId());
        if (currentMeta.isEmpty() || !currentMeta.get().isReady()) {
            rebuildService.rebuild(season, "reconciliation-meta-not-ready");
            return;
        }

        RankingRedisMeta meta = currentMeta.get();
        String token = UUID.randomUUID().toString();
        if (!redisRepository.tryAcquireReconciliationLock(season.getId(), token, properties.reconciliationLockTtl())) {
            log.info("Ranking reconciliation skipped because lock is already held seasonId={}", season.getId());
            return;
        }
        try {
            reconcileLocked(season, meta);
        } finally {
            redisRepository.releaseReconciliationLock(season.getId(), token);
        }
    }

    private void reconcileLocked(RankingSeason season, RankingRedisMeta meta) {
        Instant queryCursorUpdatedAt = meta.lastProcessedUpdatedAt() == null
                ? Instant.EPOCH
                : meta.lastProcessedUpdatedAt().minus(properties.deltaOverlap());
        // overlap 구간을 다시 조회하므로 ID cursor도 처음부터 시작한다.
        long queryCursorEntryId = 0L;

        try {
            Instant nextUpdatedAt = meta.lastProcessedUpdatedAt() == null ? Instant.EPOCH : meta.lastProcessedUpdatedAt();
            long nextEntryId = meta.lastProcessedEntryId();
            while (true) {
                List<RankingEntry> changedEntries = entryRepository.findChangedEntries(
                        season,
                        queryCursorUpdatedAt,
                        queryCursorEntryId,
                        PageRequest.of(0, properties.pageSize())
                );
                if (changedEntries.isEmpty()) {
                    break;
                }
                for (RankingEntry entry : changedEntries) {
                    if (entry.isParticipantEligible()) {
                        redisRepository.updateScore(
                                season.getId(),
                                entry.getUser().getId(),
                                entry.getScore(),
                                entry.getRegionCode(),
                                null
                        );
                    } else {
                        redisRepository.removeParticipant(season.getId(), entry.getUser().getId(), entry.getRegionCode());
                    }
                    queryCursorUpdatedAt = entry.getUpdatedAt();
                    queryCursorEntryId = entry.getId();
                    if (isAfter(entry.getUpdatedAt(), entry.getId(), nextUpdatedAt, nextEntryId)) {
                        nextUpdatedAt = entry.getUpdatedAt();
                        nextEntryId = entry.getId();
                    }
                }
                if (changedEntries.size() < properties.pageSize()) {
                    break;
                }
            }
            redisRepository.recordReconciliationSuccess(
                    season.getId(),
                    properties.schemaVersion(),
                    nextUpdatedAt,
                    nextEntryId,
                clock.instant()
            );
        } catch (RuntimeException exception) {
            redisRepository.recordReconciliationFailure(season.getId(), clock.instant());
            log.error("Ranking reconciliation failed seasonId={}", season.getId(), exception);
        }
    }

    private boolean isAfter(Instant updatedAt, long entryId, Instant baselineUpdatedAt, long baselineEntryId) {
        return updatedAt.isAfter(baselineUpdatedAt)
                || (updatedAt.equals(baselineUpdatedAt) && entryId > baselineEntryId);
    }
}
