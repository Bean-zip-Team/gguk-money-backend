package com.ggukmoney.beanzip.domain.ranking.service;

import com.ggukmoney.beanzip.domain.ranking.entity.RankingSeason;
import com.ggukmoney.beanzip.domain.ranking.redis.RankingRedisRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class RankingInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(RankingInitializer.class);

    private final RankingSeasonService seasonService;
    private final RankingBackfillService backfillService;
    private final RankingRebuildService rebuildService;
    private final RankingRedisRepository redisRepository;
    private final RankingProperties properties;
    private final Clock clock;

    @Override
    public void run(ApplicationArguments args) {
        run();
    }

    void run() {
        String token = UUID.randomUUID().toString();
        try {
            if (!isInitializationNeeded()) {
                return;
            }
            if (!redisRepository.tryAcquireInitializationLock(token, properties.initializationLockTtl())) {
                log.info("Ranking initialization skipped because another worker holds the lock");
                return;
            }
            try {
                if (!isInitializationNeeded()) {
                    return;
                }
                RankingSeason season = seasonService.getOrCreateActiveAllTimeSeason();
                backfillService.backfillActiveAllTimeFromTapProgress();
                rebuildService.rebuild(season, "startup-initializer");
            } finally {
                redisRepository.releaseInitializationLock(token);
            }
        } catch (RuntimeException exception) {
            log.error("Ranking initialization failed; application startup will continue", exception);
        }
    }

    private boolean isInitializationNeeded() {
        Optional<RankingSeason> activeSeason = seasonService.findActiveAllTimeSeason();
        if (activeSeason.isEmpty()) {
            return true;
        }
        return redisRepository.findReadyMeta(
                activeSeason.get().getId(),
                properties.schemaVersion(),
                properties.maxStaleness(),
                clock.instant()
        ).isEmpty();
    }
}
