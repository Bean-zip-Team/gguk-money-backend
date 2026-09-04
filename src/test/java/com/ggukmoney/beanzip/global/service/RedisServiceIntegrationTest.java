package com.ggukmoney.beanzip.global.service;

import com.ggukmoney.beanzip.support.RedisIntegrationTestSupport;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class RedisServiceIntegrationTest extends RedisIntegrationTestSupport {

    private RedisService service() {
        return new RedisService(redisTemplate);
    }

    @Test
    void setIfAbsentHonorsTtlAndExistingValue() {
        RedisService service = service();

        assertThat(service.setIfAbsent("lock", "owner-1", Duration.ofSeconds(5))).isTrue();
        assertThat(service.setIfAbsent("lock", "owner-2", Duration.ofSeconds(5))).isFalse();

        assertThat(service.get("lock")).contains("owner-1");
    }

    @Test
    void reverseRangeWithScoresPreservesRedisOrdering() {
        RedisService service = service();
        service.addToSortedSet("ranking", "00000000-0000-0000-0000-000000000001", 100);
        service.addToSortedSet("ranking", "ffffffff-ffff-ffff-ffff-ffffffffffff", 100);
        service.addToSortedSet("ranking", "00000000-0000-0000-0000-000000000002", 90);

        assertThat(service.getSortedSetReverseRangeWithScores("ranking", 0, 2))
                .containsExactly(
                        new RedisService.SortedSetMember("ffffffff-ffff-ffff-ffff-ffffffffffff", 100.0),
                        new RedisService.SortedSetMember("00000000-0000-0000-0000-000000000001", 100.0),
                        new RedisService.SortedSetMember("00000000-0000-0000-0000-000000000002", 90.0)
                );
    }

    @Test
    void putHashStoresSingleFieldWithoutReplacingExistingFields() {
        RedisService service = service();
        service.putAllHash("meta", java.util.Map.of("state", "READY"));

        service.putHash("meta", "lastErrorAt", "2026-07-19T00:00:00Z");

        assertThat(service.getAllHash("meta"))
                .containsEntry("state", "READY")
                .containsEntry("lastErrorAt", "2026-07-19T00:00:00Z");
    }
}
