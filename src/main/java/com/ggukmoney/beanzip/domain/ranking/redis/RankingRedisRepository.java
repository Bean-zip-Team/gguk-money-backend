package com.ggukmoney.beanzip.domain.ranking.redis;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class RankingRedisRepository {

    private static final DefaultRedisScript<Long> RELEASE_LOCK_SCRIPT = new DefaultRedisScript<>("""
            if redis.call('GET', KEYS[1]) == ARGV[1] then
                return redis.call('DEL', KEYS[1])
            end
            return 0
            """, Long.class);

    private static final DefaultRedisScript<Long> SWAP_GLOBAL_SCRIPT = new DefaultRedisScript<>("""
            local tempKey = KEYS[1]
            local liveKey = KEYS[2]
            local metaKey = KEYS[3]
            local lockKey = KEYS[4]
            local participantCount = tonumber(ARGV[1])
            if redis.call('GET', lockKey) ~= ARGV[8] then
                return -1
            end
            if participantCount > 0 then
                local tempCount = redis.call('ZCARD', tempKey)
                if tempCount ~= participantCount then
                    return 0
                end
                redis.call('DEL', liveKey)
                redis.call('RENAME', tempKey, liveKey)
            else
                redis.call('DEL', liveKey)
                redis.call('DEL', tempKey)
            end
            redis.call('HSET', metaKey,
                'state', 'READY',
                'lastReconciledAt', ARGV[2],
                'lastSuccessfulBuildAt', ARGV[3],
                'participantCount', ARGV[4],
                'schemaVersion', ARGV[5],
                'lastProcessedUpdatedAt', ARGV[6],
                'lastProcessedEntryId', ARGV[7],
                'lastErrorAt', '')
            return 1
            """, Long.class);

    private final StringRedisTemplate redisTemplate;
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
        return RankingRedisMeta.fromHash(redisTemplate.opsForHash().entries(keys.meta(seasonId)));
    }

    public void updateScore(Long seasonId, UUID userId, long score, String regionCode, String previousRegionCode) {
        String member = userId.toString();
        redisTemplate.opsForZSet().add(keys.global(seasonId), member, score);
        if (regionCode != null) {
            redisTemplate.opsForZSet().add(keys.region(seasonId, regionCode), member, score);
        }
        if (previousRegionCode != null && !previousRegionCode.equals(regionCode)) {
            redisTemplate.opsForZSet().remove(keys.region(seasonId, previousRegionCode), member);
        }
    }

    public void removeParticipant(Long seasonId, UUID userId, String regionCode) {
        String member = userId.toString();
        redisTemplate.opsForZSet().remove(keys.global(seasonId), member);
        if (regionCode != null) {
            redisTemplate.opsForZSet().remove(keys.region(seasonId, regionCode), member);
        }
    }

    public List<RankingRedisMember> findTopMembers(Long seasonId, int limit) {
        Set<ZSetOperations.TypedTuple<String>> tuples =
                redisTemplate.opsForZSet().reverseRangeWithScores(keys.global(seasonId), 0, limit - 1L);
        if (tuples == null || tuples.isEmpty()) {
            return List.of();
        }
        List<RankingRedisMember> members = new ArrayList<>();
        long rank = 1;
        for (ZSetOperations.TypedTuple<String> tuple : tuples) {
            members.add(new RankingRedisMember(
                    UUID.fromString(tuple.getValue()),
                    tuple.getScore() == null ? 0L : tuple.getScore().longValue(),
                    rank++
            ));
        }
        return members;
    }

    public Long findRank(Long seasonId, UUID userId) {
        Long zeroBased = redisTemplate.opsForZSet().reverseRank(keys.global(seasonId), userId.toString());
        return zeroBased == null ? null : zeroBased + 1;
    }

    public long findScore(Long seasonId, UUID userId) {
        Double score = redisTemplate.opsForZSet().score(keys.global(seasonId), userId.toString());
        return score == null ? 0L : score.longValue();
    }

    public long findParticipantCount(Long seasonId) {
        Long count = redisTemplate.opsForZSet().zCard(keys.global(seasonId));
        return count == null ? 0L : count;
    }

    public boolean isGlobalZSetMissing(Long seasonId) {
        return !Boolean.TRUE.equals(redisTemplate.hasKey(keys.global(seasonId)));
    }

    public void markBuilding(Long seasonId, int schemaVersion, Instant now) {
        redisTemplate.opsForHash().putAll(keys.meta(seasonId), Map.of(
                "state", RankingRedisMeta.STATE_BUILDING,
                "schemaVersion", String.valueOf(schemaVersion),
                "lastErrorAt", "",
                "lastReconciledAt", "",
                "lastSuccessfulBuildAt", ""
        ));
    }

    public void markFailed(Long seasonId, int schemaVersion, Instant now) {
        redisTemplate.opsForHash().putAll(keys.meta(seasonId), Map.of(
                "state", RankingRedisMeta.STATE_FAILED,
                "schemaVersion", String.valueOf(schemaVersion),
                "lastErrorAt", now.toString()
        ));
    }

    public void recordReconciliationFailure(Long seasonId, Instant failedAt) {
        redisTemplate.opsForHash().put(keys.meta(seasonId), "lastErrorAt", failedAt.toString());
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
        Long count = redisTemplate.opsForZSet().zCard(keys.global(seasonId));
        values.put("participantCount", String.valueOf(count == null ? 0L : count));
        redisTemplate.opsForHash().putAll(keys.meta(seasonId), values);
    }

    public boolean tryAcquireRebuildLock(Long seasonId, String token, Duration ttl) {
        return Boolean.TRUE.equals(redisTemplate.opsForValue().setIfAbsent(keys.rebuildLock(seasonId), token, ttl));
    }

    public boolean isRebuildLockOwned(Long seasonId, String token) {
        return token.equals(redisTemplate.opsForValue().get(keys.rebuildLock(seasonId)));
    }

    public void releaseRebuildLock(Long seasonId, String token) {
        releaseLock(keys.rebuildLock(seasonId), token);
    }

    public boolean tryAcquireReconciliationLock(Long seasonId, String token, Duration ttl) {
        return Boolean.TRUE.equals(redisTemplate.opsForValue().setIfAbsent(keys.reconcileLock(seasonId), token, ttl));
    }

    public void releaseReconciliationLock(Long seasonId, String token) {
        releaseLock(keys.reconcileLock(seasonId), token);
    }

    public boolean tryAcquireInitializationLock(String token, Duration ttl) {
        return Boolean.TRUE.equals(redisTemplate.opsForValue().setIfAbsent(keys.initializationLock(), token, ttl));
    }

    public void releaseInitializationLock(String token) {
        releaseLock(keys.initializationLock(), token);
    }

    public String tempGlobalKey(Long seasonId, String buildId) {
        return keys.globalBuild(seasonId, buildId);
    }

    public void addToTempGlobal(String tempKey, UUID userId, long score) {
        redisTemplate.opsForZSet().add(tempKey, userId.toString(), score);
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
        Long result = redisTemplate.execute(
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
        redisTemplate.delete(tempKey);
    }

    public record RankingRedisMember(UUID userId, long score, long rank) {
    }

    private void releaseLock(String key, String token) {
        redisTemplate.execute(RELEASE_LOCK_SCRIPT, List.of(key), token);
    }
}
