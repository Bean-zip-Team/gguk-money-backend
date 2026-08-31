package com.ggukmoney.beanzip.domain.ranking.redis;

import com.ggukmoney.beanzip.domain.ranking.entity.RankingEntry;
import com.ggukmoney.beanzip.domain.user.entity.AppUser;
import com.ggukmoney.beanzip.global.service.RedisService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class RankingRedisRepositoryTest {

    @Test
    void emptyBatchInputsDoNotCallRedis() {
        RedisService redisService = mock(RedisService.class);
        RankingRedisRepository repository = new RankingRedisRepository(redisService, new RankingRedisKeys());

        repository.applyPage(1L, List.of());
        repository.addPageToRebuild("temp", List.of());

        assertThat(repository.findRanks(1L, List.of())).isEmpty();
        verifyNoInteractions(redisService);
    }

    @Test
    void groupsPageWritesByRedisKey() {
        RedisService redisService = mock(RedisService.class);
        RankingRedisRepository repository = new RankingRedisRepository(redisService, new RankingRedisKeys());
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        UUID removed = UUID.randomUUID();

        repository.applyPage(1L, List.of(
                entry(first, 100L, "KR", true),
                entry(second, 90L, "KR", true),
                entry(removed, 80L, "KR", false)
        ));

        verify(redisService).addAllToSortedSet(
                "ggukmoney:ranking:v1:{1}:global",
                Map.of(first.toString(), 100.0, second.toString(), 90.0)
        );
        verify(redisService).addAllToSortedSet(
                "ggukmoney:ranking:v1:{1}:region:KR",
                Map.of(first.toString(), 100.0, second.toString(), 90.0)
        );
        verify(redisService).removeAllFromSortedSet(
                eq("ggukmoney:ranking:v1:{1}:global"),
                eq(Set.of(removed.toString()))
        );
        verify(redisService).removeAllFromSortedSet(
                eq("ggukmoney:ranking:v1:{1}:region:KR"),
                eq(Set.of(removed.toString()))
        );
    }

    private RankingEntry entry(UUID userId, long score, String regionCode, boolean eligible) {
        RankingEntry entry = mock(RankingEntry.class);
        AppUser user = mock(AppUser.class);
        when(user.getId()).thenReturn(userId);
        when(entry.getUser()).thenReturn(user);
        when(entry.getScore()).thenReturn(score);
        when(entry.getRegionCode()).thenReturn(regionCode);
        when(entry.isParticipantEligible()).thenReturn(eligible);
        return entry;
    }
}
