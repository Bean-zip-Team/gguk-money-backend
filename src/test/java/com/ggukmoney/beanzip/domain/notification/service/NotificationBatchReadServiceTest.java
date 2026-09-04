package com.ggukmoney.beanzip.domain.notification.service;

import com.ggukmoney.beanzip.domain.auth.repository.AuthIdentityRepository;
import com.ggukmoney.beanzip.domain.booster.repository.BoosterGrantRepository;
import com.ggukmoney.beanzip.domain.notification.repository.NotificationDeliveryRepository;
import com.ggukmoney.beanzip.domain.notification.repository.NotificationPreferenceRepository;
import com.ggukmoney.beanzip.domain.notification.repository.NotificationRankStateRepository;
import com.ggukmoney.beanzip.domain.ranking.entity.RankingSeason;
import com.ggukmoney.beanzip.domain.ranking.redis.RankingRedisMeta;
import com.ggukmoney.beanzip.domain.ranking.redis.RankingRedisRepository;
import com.ggukmoney.beanzip.domain.ranking.repository.RankingEntryRepository;
import com.ggukmoney.beanzip.domain.ranking.service.RankingProperties;
import com.ggukmoney.beanzip.domain.ranking.service.RankingSeasonService;
import com.ggukmoney.beanzip.domain.tap.repository.UserTapDailyRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotificationBatchReadServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-25T10:00:00Z");

    private final NotificationPreferenceRepository preferenceRepository = mock(NotificationPreferenceRepository.class);
    private final AuthIdentityRepository authIdentityRepository = mock(AuthIdentityRepository.class);
    private final BoosterGrantRepository boosterGrantRepository = mock(BoosterGrantRepository.class);
    private final UserTapDailyRepository userTapDailyRepository = mock(UserTapDailyRepository.class);
    private final NotificationDeliveryRepository deliveryRepository = mock(NotificationDeliveryRepository.class);
    private final NotificationRankStateRepository rankStateRepository = mock(NotificationRankStateRepository.class);
    private final RankingSeasonService rankingSeasonService = mock(RankingSeasonService.class);
    private final RankingRedisRepository rankingRedisRepository = mock(RankingRedisRepository.class);
    private final RankingEntryRepository rankingEntryRepository = mock(RankingEntryRepository.class);
    private final RankingProperties rankingProperties = new RankingProperties();
    private final NotificationBatchReadService service = new NotificationBatchReadService(
            preferenceRepository,
            authIdentityRepository,
            boosterGrantRepository,
            userTapDailyRepository,
            deliveryRepository,
            rankStateRepository,
            rankingSeasonService,
            rankingRedisRepository,
            rankingEntryRepository,
            rankingProperties,
            Clock.fixed(NOW, ZoneOffset.UTC)
    );

    @Test
    void readyFreshRedisUsesSingleBatchRankRead() {
        UUID userId = UUID.randomUUID();
        RankingSeason season = weeklySeason(1L);
        when(rankingSeasonService.findActiveWeeklySeason()).thenReturn(Optional.of(season));
        when(rankingRedisRepository.findReadyMeta(
                eq(1L), eq(rankingProperties.schemaVersion()), eq(rankingProperties.maxStaleness()), eq(NOW)
        )).thenReturn(Optional.of(readyMeta()));
        when(rankingRedisRepository.findRanks(1L, List.of(userId))).thenReturn(Map.of(userId, 4L));

        assertThat(service.loadRankPage(List.of(userId))).get()
                .extracting(data -> data.ranks().get(userId))
                .isEqualTo(4L);
        verify(rankingEntryRepository, never()).findBatchRanks(any(), anyList());
    }

    @Test
    void redisFailureFallsBackToOneWindowQuery() {
        UUID userId = UUID.randomUUID();
        RankingSeason season = weeklySeason(1L);
        RankingEntryRepository.RankingBatchRankProjection row = mock(RankingEntryRepository.RankingBatchRankProjection.class);
        when(row.getUserId()).thenReturn(userId);
        when(row.getRank()).thenReturn(7L);
        when(rankingSeasonService.findActiveWeeklySeason()).thenReturn(Optional.of(season));
        when(rankingRedisRepository.findReadyMeta(any(), any(Integer.class), any(), any()))
                .thenThrow(new IllegalStateException("redis down"));
        when(rankingEntryRepository.findBatchRanks(1L, List.of(userId))).thenReturn(List.of(row));

        assertThat(service.loadRankPage(List.of(userId))).get()
                .extracting(data -> data.ranks().get(userId))
                .isEqualTo(7L);
        verify(rankingEntryRepository).findBatchRanks(1L, List.of(userId));
    }

    @Test
    void redisAndDbFailureSkipRankPage() {
        UUID userId = UUID.randomUUID();
        RankingSeason season = weeklySeason(1L);
        when(rankingSeasonService.findActiveWeeklySeason()).thenReturn(Optional.of(season));
        when(rankingRedisRepository.findReadyMeta(any(), any(Integer.class), any(), any()))
                .thenThrow(new IllegalStateException("redis down"));
        when(rankingEntryRepository.findBatchRanks(1L, List.of(userId)))
                .thenThrow(new IllegalStateException("db down"));

        assertThat(service.loadRankPage(List.of(userId))).isEmpty();
    }

    private RankingSeason weeklySeason(Long id) {
        RankingSeason season = RankingSeason.activeWeekly(
                LocalDate.parse("2026-07-20"),
                Instant.parse("2026-07-19T15:00:00Z"),
                Instant.parse("2026-07-26T15:00:00Z")
        );
        ReflectionTestUtils.setField(season, "id", id);
        return season;
    }

    private RankingRedisMeta readyMeta() {
        return new RankingRedisMeta(
                RankingRedisMeta.STATE_READY,
                NOW,
                NOW,
                1L,
                rankingProperties.schemaVersion(),
                null,
                NOW,
                1L
        );
    }
}
