package com.ggukmoney.beanzip.domain.mission.service;

import com.ggukmoney.beanzip.domain.mission.repository.DailyRankSnapshotRepository;
import com.ggukmoney.beanzip.domain.ranking.entity.RankingSeason;
import com.ggukmoney.beanzip.domain.ranking.service.RankingSeasonService;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Constructor;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DailyRankSnapshotServiceTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    // 2026-09-21 23:55 KST
    private static final Instant NOW = Instant.parse("2026-09-21T14:55:00Z");

    private final DailyRankSnapshotRepository dailyRankSnapshotRepository = mock(DailyRankSnapshotRepository.class);
    private final RankingSeasonService rankingSeasonService = mock(RankingSeasonService.class);

    private final DailyRankSnapshotService service = new DailyRankSnapshotService(
            dailyRankSnapshotRepository, rankingSeasonService, KST, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void stampsTodaysDateInBusinessTimeZone() {
        when(rankingSeasonService.findActiveWeeklySeason()).thenReturn(Optional.of(season()));
        when(dailyRankSnapshotRepository.capture(7L, LocalDate.of(2026, 9, 21), NOW)).thenReturn(12);
        when(dailyRankSnapshotRepository.deleteOlderThan(LocalDate.of(2026, 9, 7))).thenReturn(3);

        DailyRankSnapshotService.SnapshotResult result = service.captureToday();

        // UTC 로 날짜를 잡으면 23:55 KST 가 아직 어제다. 다음 날 판정이 통째로 어긋난다.
        assertThat(result.snapshotDate()).isEqualTo(LocalDate.of(2026, 9, 21));
        assertThat(result.capturedCount()).isEqualTo(12);
        assertThat(result.deletedCount()).isEqualTo(3);
    }

    @Test
    void clearsTheDayBeforeCapturingItAgain() {
        when(rankingSeasonService.findActiveWeeklySeason()).thenReturn(Optional.of(season()));

        service.captureToday();

        // 갱신만 하면 그 사이 탈퇴했거나 점수가 0이 된 유저의 옛 순위가 남아 두 세대가 섞인다.
        InOrder inOrder = inOrder(dailyRankSnapshotRepository);
        inOrder.verify(dailyRankSnapshotRepository).deleteBySnapshotDate(LocalDate.of(2026, 9, 21));
        inOrder.verify(dailyRankSnapshotRepository).capture(7L, LocalDate.of(2026, 9, 21), NOW);
    }

    @Test
    void writesNothingWhileNoWeeklySeasonIsRunning() {
        when(rankingSeasonService.findActiveWeeklySeason()).thenReturn(Optional.empty());

        DailyRankSnapshotService.SnapshotResult result = service.captureToday();

        // 빈 스냅샷을 남기면 다음 날 "어제는 순위가 없었다"가 아니라 "순위가 0이었다"로 읽힌다.
        assertThat(result.capturedCount()).isZero();
        assertThat(result.seasonId()).isNull();
        verify(dailyRankSnapshotRepository, never()).capture(anyLong(), any(), any());
        // 보관 기간 정리는 시즌과 무관하다. 시즌이 비어 있다고 오래된 행까지 쌓아 둘 이유가 없다.
        verify(dailyRankSnapshotRepository).deleteOlderThan(LocalDate.of(2026, 9, 7));
    }

    private static RankingSeason season() {
        RankingSeason season = newInstance();
        ReflectionTestUtils.setField(season, "id", 7L);
        return season;
    }

    private static RankingSeason newInstance() {
        try {
            Constructor<RankingSeason> constructor = RankingSeason.class.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Failed to create RankingSeason", exception);
        }
    }
}
