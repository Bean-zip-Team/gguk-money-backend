package com.ggukmoney.beanzip.global.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RedisService {

    private final StringRedisTemplate redisTemplate;

    public void set(String key, String value) {
        redisTemplate.opsForValue().set(key, value);
    }

    public void set(String key, String value, Duration ttl) {
        redisTemplate.opsForValue().set(key, value, ttl);
    }

    public boolean setIfAbsent(String key, String value, Duration ttl) {
        return Boolean.TRUE.equals(redisTemplate.opsForValue().setIfAbsent(key, value, ttl));
    }

    public Optional<String> get(String key) {
        return Optional.ofNullable(redisTemplate.opsForValue().get(key));
    }

    public void delete(String key) {
        redisTemplate.delete(key);
    }

    public boolean exists(String key) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }

    public Set<String> keys(String pattern) {
        Set<String> found = redisTemplate.keys(pattern);
        return found == null ? Set.of() : found;
    }

    public void addToSet(String key, String member) {
        redisTemplate.opsForSet().add(key, member);
    }

    public void removeFromSet(String key, String member) {
        redisTemplate.opsForSet().remove(key, member);
    }

    public Set<String> getSet(String key) {
        Set<String> members = redisTemplate.opsForSet().members(key);
        return members == null ? Set.of() : members;
    }

    public boolean isSetMember(String key, String member) {
        return Boolean.TRUE.equals(redisTemplate.opsForSet().isMember(key, member));
    }

    public void addToSortedSet(String key, String member, double score) {
        redisTemplate.opsForZSet().add(key, member, score);
    }

    public void removeFromSortedSet(String key, String member) {
        redisTemplate.opsForZSet().remove(key, member);
    }

    public Set<String> getSortedSetRange(String key, long start, long end) {
        Set<String> range = redisTemplate.opsForZSet().range(key, start, end);
        return range == null ? Set.of() : range;
    }

    public Double getSortedSetScore(String key, String member) {
        return redisTemplate.opsForZSet().score(key, member);
    }

    public Double incrementSortedSetScore(String key, String member, double delta) {
        return redisTemplate.opsForZSet().incrementScore(key, member, delta);
    }

    public Long getSortedSetRank(String key, String member) {
        return redisTemplate.opsForZSet().reverseRank(key, member);
    }

    public Set<String> getSortedSetReverseRange(String key, long start, long end) {
        Set<String> range = redisTemplate.opsForZSet().reverseRange(key, start, end);
        return range == null ? Set.of() : range;
    }

    public List<SortedSetMember> getSortedSetReverseRangeWithScores(String key, long start, long end) {
        Set<ZSetOperations.TypedTuple<String>> range = redisTemplate.opsForZSet().reverseRangeWithScores(key, start, end);
        if (range == null || range.isEmpty()) {
            return List.of();
        }
        List<SortedSetMember> members = new ArrayList<>();
        for (ZSetOperations.TypedTuple<String> tuple : range) {
            if (tuple.getValue() != null && tuple.getScore() != null) {
                members.add(new SortedSetMember(tuple.getValue(), tuple.getScore()));
            }
        }
        return members;
    }

    public Long getSortedSetSize(String key) {
        return redisTemplate.opsForZSet().zCard(key);
    }

    public void expire(String key, Duration ttl) {
        redisTemplate.expire(key, ttl);
    }

    public Duration getTtl(String key) {
        Long seconds = redisTemplate.getExpire(key);
        return seconds == null ? null : Duration.ofSeconds(seconds);
    }

    public void putAllHash(String key, Map<String, String> fields) {
        redisTemplate.opsForHash().putAll(key, fields);
    }

    public void putHash(String key, String field, String value) {
        redisTemplate.opsForHash().put(key, field, value);
    }

    public Map<String, String> getAllHash(String key) {
        Map<Object, Object> entries = redisTemplate.opsForHash().entries(key);
        return entries.entrySet().stream()
                .collect(Collectors.toMap(entry -> entry.getKey().toString(), entry -> entry.getValue().toString()));
    }

    public <T> T executeScript(RedisScript<T> script, List<String> keys, Object... args) {
        return redisTemplate.execute(script, keys, args);
    }

    public record SortedSetMember(String member, double score) {
    }
}
