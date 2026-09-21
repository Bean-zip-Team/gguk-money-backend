package com.ggukmoney.beanzip.domain.ranking.boost;

import com.ggukmoney.beanzip.global.config.AppConfigBatchLoader;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class SystemRankingBoostPolicyTest {
    private final AppConfigBatchLoader loader = mock(AppConfigBatchLoader.class);
    private final SystemRankingBoostPolicy policy = new SystemRankingBoostPolicy(loader, new ObjectMapper());
    private final Instant now = Instant.parse("2026-09-18T10:00:00Z");

    @Test
    void dynamicThresholdIsNotHardcodedAndEveryReadObservesKillSwitch() {
        when(loader.load(Set.of(SystemRankingBoostPolicy.KEY), now)).thenReturn(Map.of(SystemRankingBoostPolicy.KEY,
                "{\"enabled\":true,\"internalUserIds\":[],\"minimumLeaderScore\":17,\"minIncrement\":2,\"maxIncrement\":4}"));
        assertThat(policy.load(now).orElseThrow().minimumLeaderScore()).isEqualTo(17L);
        assertThat(policy.load(now).orElseThrow().enabled()).isTrue();
        when(loader.load(Set.of(SystemRankingBoostPolicy.KEY), now)).thenReturn(Map.of(SystemRankingBoostPolicy.KEY,
                "{\"enabled\":false,\"internalUserIds\":[],\"minimumLeaderScore\":17,\"minIncrement\":2,\"maxIncrement\":4}"));
        assertThat(policy.load(now).orElseThrow().enabled()).isFalse();
    }

    @Test
    void missingMalformedPartialAndUnavailablePolicyFailClosed() {
        for (String raw : List.of("{}", "null", "{\"enabled\":true}",
                "{\"enabled\":true,\"internalUserIds\":[\"bad\"],\"minimumLeaderScore\":10,\"minIncrement\":200,\"maxIncrement\":100}")) {
            when(loader.load(Set.of(SystemRankingBoostPolicy.KEY), now)).thenReturn(Map.of(SystemRankingBoostPolicy.KEY, raw));
            assertThat(policy.load(now)).isEmpty();
        }
        when(loader.load(Set.of(SystemRankingBoostPolicy.KEY), now)).thenReturn(Map.of());
        assertThat(policy.load(now)).isEmpty();
        when(loader.load(Set.of(SystemRankingBoostPolicy.KEY), now)).thenThrow(new IllegalStateException("offline"));
        assertThat(policy.load(now)).isEmpty();
    }

    @Test
    void scheduleIsKstFiveMinuteSlotAndIncrementIncludesBothBounds() {
        RankingBoostRandom random = new RankingBoostRandom();
        LocalDate date = LocalDate.of(2026, 9, 18);
        for (int i = 0; i < 200; i++) {
            ZonedDateTime scheduled = random.scheduledAt(date).atZone(ZoneId.of("Asia/Seoul"));
            assertThat(scheduled.toLocalDate()).isEqualTo(date);
            assertThat(scheduled.getHour()).isBetween(18, 21);
            assertThat(scheduled.getMinute() % 5).isZero();
            assertThat(random.increment(200, 500)).isBetween(200, 500);
        }
        assertThat(random.increment(200, 200)).isEqualTo(200);
    }

    @Test
    void productionGateCannotBeOpenedByAppConfigAndCloseWindowBoundaryIsTestable() {
        var gate = new SystemRankingBoostRolloutGate();
        var season = com.ggukmoney.beanzip.domain.ranking.entity.RankingSeason.activeWeekly(LocalDate.of(2026, 9, 14),
                Instant.parse("2026-09-13T15:00:00Z"), Instant.parse("2026-09-20T15:00:00Z"));
        assertThat(gate.permits(now, season)).isFalse();
        assertThat(SystemRankingBoostRolloutGate.outsideCloseWindow(season,
                Instant.parse("2026-09-20T12:59:59Z"), Duration.ofHours(2))).isTrue();
        assertThat(SystemRankingBoostRolloutGate.outsideCloseWindow(season,
                Instant.parse("2026-09-20T13:00:00Z"), Duration.ofHours(2))).isFalse();
    }
}
