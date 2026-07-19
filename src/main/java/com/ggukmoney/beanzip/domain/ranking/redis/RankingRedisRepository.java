package com.ggukmoney.beanzip.domain.ranking.redis;

import com.ggukmoney.beanzip.global.service.RedisService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class RankingRedisRepository {

    private static final RedisScript<Long> RELEASE_LOCK_SCRIPT =
            RedisScript.of(new ClassPathResource("scripts/ranking-release-lock.lua"), Long.class);
    private static final RedisScript<Long> SWAP_GLOBAL_SCRIPT =
            RedisScript.of(new ClassPathResource("scripts/ranking-swap-global.lua"), Long.class);

    private final RedisService redisService;
    private final RankingRedisKeys keys;

    public Optional<RankingRedisMeta> findReadyMeta(
            Long seasonId,
            int schemaVersion,
            Duration maxStaleness,
            Instant now
    ) {
        return findMeta(seasonId)
                .filter(meta -> meta.schemaVersion() == schemaVersion)
                .filter(RankingRedisMeta::isReady)
                .filter(meta -> meta.isFresh(now, maxStaleness));
    }

    public Optional<RankingRedisMeta> findMeta(Long seasonId) {
        return RankingRedisMeta.fromHash(redisService.getAllHash(keys.meta(seasonId)));
    }

    public void updateScore(Long seasonId, UUID userId, long score, String regionCode, String previousRegionCode) {
        String member = userId.toString();
        redisService.addToSortedSet(keys.global(seasonId), member, score);
        if (regionCode != null) {
            redisService.addToSortedSet(keys.region(seasonId, regionCode), member, score);
        }
        if (previousRegionCode != null && !previousRegionCode.equals(regionCode)) {
            redisService.removeFromSortedSet(keys.region(seasonId, previousRegionCode), member);
        }
    }

    public void removeParticipant(Long seasonId, UUID userId, String regionCode) {
        String member = userId.toString();
        redisService.removeFromSortedSet(keys.global(seasonId), member);
        if (regionCode != null) {
            redisService.removeFromSortedSet(keys.region(seasonId, regionCode), member);
        }
    }

    public List<RankingRedisMember> findTopMembers(Long seasonId, int limit) {
        List<RedisService.SortedSetMember> sortedMembers =
                redisService.getSortedSetReverseRangeWithScores(keys.global(seasonId), 0, limit - 1L);
        if (sortedMembers.isEmpty()) {
            return List.of();
        }
        List<RankingRedisMember> members = new ArrayList<>();
        long rank = 1;
        for (RedisService.SortedSetMember sortedMember : sortedMembers) {
            members.add(new RankingRedisMember(
                    UUID.fromString(sortedMember.member()),
                    (long) sortedMember.score(),
                    rank++
            ));
        }
        return members;
    }

    public Long findRank(Long seasonId, UUID userId) {
        Long zeroBased = redisService.getSortedSetRank(keys.global(seasonId), userId.toString());
        return zeroBased == null ? null : zeroBased + 1;
    }

    public long findScore(Long seasonId, UUID userId) {
        Double score = redisService.getSortedSetScore(keys.global(seasonId), userId.toString());
        return score == null ? 0L : score.longValue();
    }

    public long findParticipantCount(Long seasonId) {
        Long count = redisService.getSortedSetSize(keys.global(seasonId));
        return count == null ? 0L : count;
    }

    public boolean isGlobalZSetMissing(Long seasonId) {
        return !redisService.exists(keys.global(seasonId));
    }

    public void markBuilding(Long seasonId, int schemaVersion, Instant now) {
        redisService.putAllHash(keys.meta(seasonId), Map.of(
                "state", RankingRedisMeta.STATE_BUILDING,
                "schemaVersion", String.valueOf(schemaVersion),
                "lastErrorAt", "",
                "lastReconciledAt", "",
                "lastSuccessfulBuildAt", ""
        ));
    }

    public void markFailed(Long seasonId, int schemaVersion, Instant now) {
        redisService.putAllHash(keys.meta(seasonId), Map.of(
                "state", RankingRedisMeta.STATE_FAILED,
                "schemaVersion", String.valueOf(schemaVersion),
                "lastErrorAt", now.toString()
        ));
    }

    public void recordReconciliationFailure(Long seasonId, Instant failedAt) {
        redisService.putHash(keys.meta(seasonId), "lastErrorAt", failedAt.toString());
    }

    public void recordReconciliationSuccess(
            Long seasonId,
            int schemaVersion,
            Instant lastProcessedUpdatedAt,
            long lastProcessedEntryId,
            Instant reconciledAt
    ) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("state", RankingRedisMeta.STATE_READY);
        values.put("schemaVersion", String.valueOf(schemaVersion));
        values.put("lastReconciledAt", reconciledAt.toString());
        values.put("lastProcessedUpdatedAt", lastProcessedUpdatedAt.toString());
        values.put("lastProcessedEntryId", String.valueOf(lastProcessedEntryId));
        Long count = redisService.getSortedSetSize(keys.global(seasonId));
        values.put("participantCount", String.valueOf(count == null ? 0L : count));
        redisService.putAllHash(keys.meta(seasonId), values);
    }

    public boolean tryAcquireRebuildLock(Long seasonId, String token, Duration ttl) {
        return redisService.setIfAbsent(keys.rebuildLock(seasonId), token, ttl);
    }

    public boolean isRebuildLockOwned(Long seasonId, String token) {
        return redisService.get(keys.rebuildLock(seasonId)).map(token::equals).orElse(false);
    }

    public void releaseRebuildLock(Long seasonId, String token) {
        releaseLock(keys.rebuildLock(seasonId), token);
    }

    public boolean tryAcquireReconciliationLock(Long seasonId, String token, Duration ttl) {
        return redisService.setIfAbsent(keys.reconcileLock(seasonId), token, ttl);
    }

    public void releaseReconciliationLock(Long seasonId, String token) {
        releaseLock(keys.reconcileLock(seasonId), token);
    }

    public boolean tryAcquireInitializationLock(String token, Duration ttl) {
        return redisService.setIfAbsent(keys.initializationLock(), token, ttl);
    }

    public void releaseInitializationLock(String token) {
        releaseLock(keys.initializationLock(), token);
    }

    public String tempGlobalKey(Long seasonId, String buildId) {
        return keys.globalBuild(seasonId, buildId);
    }

    public void addToTempGlobal(String tempKey, UUID userId, long score) {
        redisService.addToSortedSet(tempKey, userId.toString(), score);
    }

    public boolean swapTempGlobalToLive(
            Long seasonId,
            String tempKey,
            long participantCount,
            int schemaVersion,
            Instant now,
            Instant lastProcessedUpdatedAt,
            long lastProcessedEntryId,
            String rebuildLockToken
    ) {
        Long result = redisService.executeScript(
                SWAP_GLOBAL_SCRIPT,
                List.of(tempKey, keys.global(seasonId), keys.meta(seasonId), keys.rebuildLock(seasonId)),
                String.valueOf(participantCount),
                now.toString(),
                now.toString(),
                String.valueOf(participantCount),
                String.valueOf(schemaVersion),
                lastProcessedUpdatedAt.toString(),
                String.valueOf(lastProcessedEntryId),
                rebuildLockToken
        );
        return result != null && result == 1L;
    }

    public void deleteTemp(String tempKey) {
        redisService.delete(tempKey);
    }

    public record RankingRedisMember(UUID userId, long score, long rank) {
    }

    private void releaseLock(String key, String token) {
        redisService.executeScript(RELEASE_LOCK_SCRIPT, List.of(key), token);
    }
}
