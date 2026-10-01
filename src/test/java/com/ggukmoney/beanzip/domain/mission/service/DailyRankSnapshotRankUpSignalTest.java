package com.ggukmoney.beanzip.domain.mission.service;

import com.ggukmoney.beanzip.domain.mission.entity.DailyRankSnapshot;
import com.ggukmoney.beanzip.domain.mission.repository.DailyRankSnapshotRepository;
import com.ggukmoney.beanzip.domain.ranking.entity.RankingSeason;
import com.ggukmoney.beanzip.domain.ranking.repository.RankingEntryRepository;
import com.ggukmoney.beanzip.domain.ranking.service.RankingSeasonService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Constructor;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DailyRankSnapshotRankUpSignalTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 22);
    private static final LocalDate YESTERDAY = LocalDate.of(2026, 9, 21);
    private static final long THIS_SEASON_ID = 7L;
    private static final long LAST_SEASON_ID = 6L;

    private final DailyRankSnapshotRepository dailyRankSnapshotRepository = mock(DailyRankSnapshotRepository.class);
    private final RankingSeasonService rankingSeasonService = mock(RankingSeasonService.class);
    private final RankingEntryRepository rankingEntryRepository = mock(RankingEntryRepository.class);

    private final DailyRankSnapshotRankUpSignal signal = new DailyRankSnapshotRankUpSignal(
            dailyRankSnapshotRepository, rankingSeasonService, rankingEntryRepository);

    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void stubActiveSeason() {
        lenient().when(rankingSeasonService.findActiveWeeklySeason()).thenReturn(Optional.of(season(THIS_SEASON_ID)));
    }

    @Test
    void measuresHowManyPlacesTheUserClimbedSinceYesterday() {
        givenYesterdayRank(12, THIS_SEASON_ID);
        givenCurrentRank(5);

        assertThat(signal.rankUpOf(userId, TODAY)).contains(7L);
    }

    @Test
    void reportsNoRiseWhenTheUserSlippedDown() {
        givenYesterdayRank(5, THIS_SEASON_ID);
        givenCurrentRank(9);

        // 음수를 돌려주면 진행도 표시가 이상해진다. 내려간 것은 상승 0 으로 본다.
        assertThat(signal.rankUpOf(userId, TODAY)).contains(0L);
    }

    @Test
    void refusesToJudgeAcrossAWeeklyReset() {
        givenYesterdayRank(12, LAST_SEASON_ID);
        givenCurrentRank(5);

        // 월요일이다. 시즌이 초기화되어 전원이 0점에서 다시 시작하므로 비교하면 모두가 크게 오른 것처럼 잡힌다.
        assertThat(signal.rankUpOf(userId, TODAY)).isEmpty();
    }

    @Test
    void refusesToJudgeWhenYesterdayHasNoSnapshot() {
        when(dailyRankSnapshotRepository.findByUserIdAndSnapshotDate(userId, YESTERDAY)).thenReturn(Optional.empty());

        // 오늘 처음 참가한 유저가 여기 해당한다. 비교 기준이 없으면 판정하지 않는다.
        assertThat(signal.rankUpOf(userId, TODAY)).isEmpty();
    }

    @Test
    void refusesToJudgeWhenTheUserHasNoRankThisWeek() {
        givenYesterdayRank(12, THIS_SEASON_ID);
        when(rankingEntryRepository.findMyParticipant(any(RankingSeason.class), eq(userId)))
                .thenReturn(Optional.empty());

        assertThat(signal.rankUpOf(userId, TODAY)).isEmpty();
    }

    @Test
    void reportsNoRiseWhenTheRankDidNotMove() {
        givenYesterdayRank(5, THIS_SEASON_ID);
        givenCurrentRank(5);

        assertThat(signal.rankUpOf(userId, TODAY)).contains(0L);
    }

    @Test
    void hidesTheMissionInsteadOfFailingWhenRankingLookupBlowsUp() {
        givenYesterdayRank(12, THIS_SEASON_ID);
        when(rankingEntryRepository.findMyParticipant(any(RankingSeason.class), eq(userId)))
                .thenThrow(new IllegalStateException("ranking is down"));

        // 랭킹이 흔들렸다고 미션 목록과 보상 수령까지 500 으로 떨어뜨리지 않는다.
        assertThat(signal.rankUpOf(userId, TODAY)).isEmpty();
    }

    @Test
    void refusesToJudgeWhileNoWeeklySeasonIsRunning() {
        when(rankingSeasonService.findActiveWeeklySeason()).thenReturn(Optional.empty());

        assertThat(signal.rankUpOf(userId, TODAY)).isEmpty();
    }

    private void givenYesterdayRank(int rank, long seasonId) {
        when(dailyRankSnapshotRepository.findByUserIdAndSnapshotDate(userId, YESTERDAY))
                .thenReturn(Optional.of(snapshot(rank, seasonId)));
    }

    /** 현재 순위는 "나보다 앞선 사람 수 + 1" 로 구한다. 랭킹 화면의 DB 경로와 같다. */
    private void givenCurrentRank(long rank) {
        RankingEntryRepository.RankingParticipantRow row =
                new RankingEntryRepository.RankingParticipantRow(userId, "nickname", null, 500L);
        when(rankingEntryRepository.findMyParticipant(any(RankingSeason.class), eq(userId)))
                .thenReturn(Optional.of(row));
        when(rankingEntryRepository.countParticipantsAhead(any(RankingSeason.class), eq(500L), eq(userId.toString())))
                .thenReturn(rank - 1);
    }

    private DailyRankSnapshot snapshot(int rank, long seasonId) {
        DailyRankSnapshot snapshot = newInstance(DailyRankSnapshot.class);
        ReflectionTestUtils.setField(snapshot, "userId", userId);
        ReflectionTestUtils.setField(snapshot, "snapshotDate", YESTERDAY);
        ReflectionTestUtils.setField(snapshot, "seasonId", seasonId);
        ReflectionTestUtils.setField(snapshot, "rankValue", rank);
        return snapshot;
    }

    private static RankingSeason season(long id) {
        RankingSeason season = newInstance(RankingSeason.class);
        ReflectionTestUtils.setField(season, "id", id);
        return season;
    }

    private static <T> T newInstance(Class<T> type) {
        try {
            Constructor<T> constructor = type.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Failed to create test entity " + type.getSimpleName(), exception);
        }
    }
}
