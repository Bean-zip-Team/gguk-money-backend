package com.ggukmoney.beanzip.domain.ranking.redis;

import com.ggukmoney.beanzip.support.RedisIntegrationTestSupport;
import com.ggukmoney.beanzip.global.service.RedisService;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RankingRedisRepositoryIntegrationTest extends RedisIntegrationTestSupport {

    private RankingRedisRepository repository() {
        return new RankingRedisRepository(new RedisService(redisTemplate), new RankingRedisKeys());
    }

    @Test
    void rankingLuaScriptsAreStoredAsClasspathResources() {
        assertThat(new ClassPathResource("scripts/ranking-release-lock.lua").exists()).isTrue();
        assertThat(new ClassPathResource("scripts/ranking-swap-global.lua").exists()).isTrue();
    }

    @Test
    void updatesScoreRemovesParticipantAndReadsZRevRank() {
        RankingRedisRepository repository = repository();
        UUID first = UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff");
        UUID second = UUID.fromString("00000000-0000-0000-0000-000000000001");

        repository.updateScore(1L, second, 100L, null, null);
        repository.updateScore(1L, first, 100L, null, null);

        assertThat(repository.findTopMembers(1L, 10)).extracting(RankingRedisRepository.RankingRedisMember::userId)
                .containsExactly(first, second);
        assertThat(repository.findRank(1L, second)).isEqualTo(2L);

        repository.removeParticipant(1L, first, null);

        assertThat(repository.findTopMembers(1L, 10)).extracting(RankingRedisRepository.RankingRedisMember::userId)
                .containsExactly(second);
    }

    @Test
    void appliesMixedPageAndFindsRanksWithOneBasedValues() {
        RankingRedisRepository repository = repository();
        UUID highest = UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff");
        UUID tiedLower = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID removed = UUID.randomUUID();
        repository.updateScore(1L, removed, 300L, null, null);

        com.ggukmoney.beanzip.domain.ranking.entity.RankingEntry first =
                rankingEntry(highest, 100L, true);
        com.ggukmoney.beanzip.domain.ranking.entity.RankingEntry second =
                rankingEntry(tiedLower, 100L, true);
        com.ggukmoney.beanzip.domain.ranking.entity.RankingEntry excluded =
                rankingEntry(removed, 500L, false);

        repository.applyPage(1L, List.of(first, second, excluded));
        repository.applyPage(1L, List.of(first, second, excluded));

        assertThat(repository.findRanks(1L, List.of(tiedLower, removed, highest, UUID.randomUUID())))
                .containsExactlyInAnyOrderEntriesOf(Map.of(highest, 1L, tiedLower, 2L));
        assertThat(repository.findTopMembers(1L, 10))
                .extracting(RankingRedisRepository.RankingRedisMember::userId)
                .containsExactly(highest, tiedLower);
    }

    @Test
    void bulkAddsRebuildPageAndReturnsEmptyRanksForEmptyInput() {
        RankingRedisRepository repository = repository();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        String tempKey = repository.tempGlobalKey(1L, "build");

        repository.addPageToRebuild(tempKey, List.of(
                rankingEntry(first, 200L, true),
                rankingEntry(second, 100L, true)
        ));

        assertThat(redisTemplate.opsForZSet().reverseRange(tempKey, 0, -1))
                .isEqualTo(Set.of(first.toString(), second.toString()));
        assertThat(repository.findRanks(1L, List.of())).isEmpty();
    }

    @Test
    void swapsEmptyRankingAndWritesReadyMetaAtomically() {
        RankingRedisRepository repository = repository();
        String token = "owner";
        assertThat(repository.tryAcquireRebuildLock(1L, token, Duration.ofSeconds(5))).isTrue();

        boolean swapped = repository.swapTempGlobalToLive(
                1L,
                "ggukmoney:ranking:v1:{1}:global:build:test",
                0L,
                1,
                Instant.parse("2026-07-19T01:00:00Z"),
                Instant.EPOCH,
                0L,
                token
        );

        assertThat(swapped).isTrue();
        RankingRedisMeta meta = repository.findMeta(1L).orElseThrow();
        assertThat(meta.isReady()).isTrue();
        assertThat(meta.participantCount()).isZero();
        assertThat(repository.isGlobalZSetMissing(1L)).isTrue();
    }

    @Test
    void keepsExistingLiveSetWhenSwapCountValidationFails() {
        RankingRedisRepository repository = repository();
        UUID liveUser = UUID.randomUUID();
        String tempKey = "ggukmoney:ranking:v1:{1}:global:build:test";
        String token = "owner";
        assertThat(repository.tryAcquireRebuildLock(1L, token, Duration.ofSeconds(5))).isTrue();
        repository.updateScore(1L, liveUser, 100L, null, null);
        repository.addToTempGlobal(tempKey, UUID.randomUUID(), 200L);

        boolean swapped = repository.swapTempGlobalToLive(
                1L,
                tempKey,
                2L,
                1,
                Instant.parse("2026-07-19T01:00:00Z"),
                Instant.EPOCH,
                0L,
                token
        );

        assertThat(swapped).isFalse();
        assertThat(repository.findTopMembers(1L, 10)).extracting(RankingRedisRepository.RankingRedisMember::userId)
                .containsExactly(liveUser);
        assertThat(repository.findMeta(1L)).isEmpty();
    }

    @Test
    void compareAndDeleteLocksOnlyReleaseOwnerToken() throws Exception {
        RankingRedisRepository repository = repository();
        String firstToken = "owner-1";
        String secondToken = "owner-2";

        assertThat(repository.tryAcquireRebuildLock(1L, firstToken, Duration.ofSeconds(5))).isTrue();
        repository.releaseRebuildLock(1L, "other-owner");
        assertThat(repository.isRebuildLockOwned(1L, firstToken)).isTrue();

        repository.releaseRebuildLock(1L, firstToken);
        assertThat(repository.tryAcquireRebuildLock(1L, secondToken, Duration.ofSeconds(5))).isTrue();
        repository.releaseRebuildLock(1L, firstToken);

        assertThat(repository.isRebuildLockOwned(1L, secondToken)).isTrue();

        assertThat(repository.tryAcquireRebuildLock(2L, firstToken, Duration.ofMillis(300))).isTrue();
        Thread.sleep(500);
        assertThat(repository.tryAcquireRebuildLock(2L, secondToken, Duration.ofSeconds(5))).isTrue();
        repository.releaseRebuildLock(2L, firstToken);

        assertThat(repository.isRebuildLockOwned(2L, secondToken)).isTrue();
    }

    @Test
    void reconciliationLockUsesCompareAndDeleteRelease() {
        RankingRedisRepository repository = repository();

        assertThat(repository.tryAcquireReconciliationLock(1L, "owner", Duration.ofSeconds(5))).isTrue();
        repository.releaseReconciliationLock(1L, "other-owner");
        assertThat(repository.tryAcquireReconciliationLock(1L, "new-owner", Duration.ofSeconds(5))).isFalse();

        repository.releaseReconciliationLock(1L, "owner");

        assertThat(repository.tryAcquireReconciliationLock(1L, "new-owner", Duration.ofSeconds(5))).isTrue();
    }

    private com.ggukmoney.beanzip.domain.ranking.entity.RankingEntry rankingEntry(
            UUID userId,
            long score,
            boolean eligible
    ) {
        com.ggukmoney.beanzip.domain.ranking.entity.RankingEntry entry =
                org.mockito.Mockito.mock(com.ggukmoney.beanzip.domain.ranking.entity.RankingEntry.class);
        com.ggukmoney.beanzip.domain.user.entity.AppUser user =
                org.mockito.Mockito.mock(com.ggukmoney.beanzip.domain.user.entity.AppUser.class);
        org.mockito.Mockito.when(user.getId()).thenReturn(userId);
        org.mockito.Mockito.when(entry.getUser()).thenReturn(user);
        org.mockito.Mockito.when(entry.getScore()).thenReturn(score);
        org.mockito.Mockito.when(entry.isParticipantEligible()).thenReturn(eligible);
        return entry;
    }
}
