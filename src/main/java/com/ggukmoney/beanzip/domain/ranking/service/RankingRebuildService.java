package com.ggukmoney.beanzip.domain.ranking.service;

import com.ggukmoney.beanzip.domain.ranking.entity.RankingEntry;
import com.ggukmoney.beanzip.domain.ranking.entity.RankingSeason;
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
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RankingRebuildService {

    private static final Logger log = LoggerFactory.getLogger(RankingRebuildService.class);

    private final RankingSeasonService seasonService;
    private final RankingEntryRepository entryRepository;
    private final RankingRedisRepository redisRepository;
    private final RankingProperties properties;
    private final Clock clock;

    @Transactional(readOnly = true)
    public boolean rebuildActiveWeekly(String reason) {
        return seasonService.findActiveWeeklySeason()
                .filter(value -> rebuild(value, reason))
                .isPresent();
    }

    @Deprecated
    @Transactional(readOnly = true)
    public boolean rebuildActiveAllTime(String reason) {
        return seasonService.findActiveAllTimeSeason()
                .filter(value -> rebuild(value, reason))
                .isPresent();
    }

    @Transactional(readOnly = true)
    public boolean rebuild(RankingSeason season, String reason) {
        String token = UUID.randomUUID().toString();
        if (!redisRepository.tryAcquireRebuildLock(season.getId(), token, properties.rebuildLockTtl())) {
            return false;
        }
        String tempKey = redisRepository.tempGlobalKey(season.getId(), UUID.randomUUID().toString());
        try {
            Instant now = clock.instant();
            Cursor highWaterCursor = lastCursor(season);
            redisRepository.markBuilding(season.getId(), properties.schemaVersion(), now);
            long participantCount = entryRepository.countParticipants(season);
            int page = 0;
            long loadedCount = 0;
            while (participantCount > 0) {
                List<RankingEntry> entries = entryRepository.findRebuildEntries(
                        season,
                        PageRequest.of(page, properties.pageSize())
                );
                if (entries.isEmpty()) {
                    break;
                }
                for (RankingEntry entry : entries) {
                    redisRepository.addToTempGlobal(tempKey, entry.getUser().getId(), entry.getScore());
                    loadedCount++;
                }
                if (entries.size() < properties.pageSize()) {
                    break;
                }
                page++;
            }

            if (participantCount > 0 && loadedCount != participantCount) {
                throw new IllegalStateException("ranking rebuild participant count mismatch");
            }

            if (!redisRepository.isRebuildLockOwned(season.getId(), token)) {
                throw new IllegalStateException("ranking rebuild lock lost before swap");
            }
            boolean swapped = redisRepository.swapTempGlobalToLive(
                    season.getId(),
                    tempKey,
                    participantCount,
                    properties.schemaVersion(),
                    now,
                    highWaterCursor.updatedAt(),
                    highWaterCursor.entryId(),
                    token
            );
            if (!swapped) {
                throw new IllegalStateException("ranking rebuild swap failed");
            }
            log.info(
                    "Ranking rebuild succeeded seasonId={} participantCount={} reason={}",
                    season.getId(),
                    participantCount,
                    reason
            );
            return true;
        } catch (RuntimeException exception) {
            redisRepository.deleteTemp(tempKey);
            redisRepository.markFailed(season.getId(), properties.schemaVersion(), clock.instant());
            log.error("Ranking rebuild failed seasonId={} reason={}", season.getId(), reason, exception);
            return false;
        } finally {
            redisRepository.releaseRebuildLock(season.getId(), token);
        }
    }

    private Cursor lastCursor(RankingSeason season) {
        return entryRepository.findLastCursorEntry(season, PageRequest.of(0, 1)).stream()
                .findFirst()
                .map(entry -> new Cursor(entry.getUpdatedAt(), entry.getId()))
                .orElse(new Cursor(Instant.EPOCH, 0L));
    }

    private record Cursor(Instant updatedAt, long entryId) {
    }
}
