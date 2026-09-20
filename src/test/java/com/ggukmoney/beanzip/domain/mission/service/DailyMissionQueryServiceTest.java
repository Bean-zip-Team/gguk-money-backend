package com.ggukmoney.beanzip.domain.mission.service;

import com.ggukmoney.beanzip.domain.mission.entity.MissionDefinition;
import com.ggukmoney.beanzip.domain.promotion.dto.response.MissionListResponse;
import com.ggukmoney.beanzip.domain.tap.entity.UserTapDaily;
import com.ggukmoney.beanzip.domain.tap.repository.UserTapDailyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DailyMissionQueryServiceTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final LocalDate TODAY = LocalDate.of(2026, 9, 21);

    private final MissionDefinitionCatalog catalog = mock(MissionDefinitionCatalog.class);
    private final UserTapDailyRepository userTapDailyRepository = mock(UserTapDailyRepository.class);
    private final RankUpSignal rankUpSignal = mock(RankUpSignal.class);
    private final NotificationOptInSignal notificationOptInSignal = mock(NotificationOptInSignal.class);

    // 2026-09-21 14:00 KST
    private final Clock clock = Clock.fixed(Instant.parse("2026-09-21T05:00:00Z"), ZoneOffset.UTC);

    private final DailyMissionQueryService service = new DailyMissionQueryService(
            catalog, userTapDailyRepository, rankUpSignal, notificationOptInSignal, KST, clock);

    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void stubDefaults() {
        lenient().when(catalog.activeDefinitions()).thenReturn(List.of(
                definition("NOTIFICATION_OPT_IN", MissionDefinition.MissionType.NOTIFICATION_OPT_IN,
                        MissionDefinition.PeriodType.ONE_TIME, 1, 100),
                definition("ATTENDANCE", MissionDefinition.MissionType.ATTENDANCE, MissionDefinition.PeriodType.DAILY, 1, 100),
                definition("TAP_500", MissionDefinition.MissionType.TAP_COUNT, MissionDefinition.PeriodType.DAILY, 500, 15),
                definition("TAP_1000", MissionDefinition.MissionType.TAP_COUNT, MissionDefinition.PeriodType.DAILY, 1000, 20),
                definition("RANK_UP_5", MissionDefinition.MissionType.RANK_UP, MissionDefinition.PeriodType.DAILY, 5, 100)
        ));
        lenient().when(userTapDailyRepository.findByUserIdAndTapDate(userId, TODAY)).thenReturn(Optional.empty());
        lenient().when(rankUpSignal.rankUpOf(eq(userId), any())).thenReturn(Optional.empty());
        lenient().when(notificationOptInSignal.agreed(userId)).thenReturn(false);
        lenient().when(userTapDailyRepository.findRecentTapDates(eq(userId), eq(TODAY), any(Pageable.class)))
                .thenReturn(List.of());
    }

    @Test
    void countsTodayTapsAgainstEachTapMissionThreshold() {
        givenTodayTaps(700);

        List<MissionListResponse.Mission> missions = service.feedOf(userId).missions();

        assertThat(missionOf(missions, "TAP_500"))
                .extracting(MissionListResponse.Mission::current, MissionListResponse.Mission::status)
                .containsExactly(700L, MissionListResponse.Status.ACHIEVED);
        assertThat(missionOf(missions, "TAP_1000"))
                .extracting(MissionListResponse.Mission::current, MissionListResponse.Mission::status)
                .containsExactly(700L, MissionListResponse.Status.IN_PROGRESS);
    }

    @Test
    void treatsTodaysTapRowAsAttendanceSoOpeningTheAppIsEnough() {
        givenTodayTaps(0);

        // 홈이 오늘 탭 상태를 조회하면서 행을 만든다. 탭을 한 번도 안 눌러도 출석이다.
        assertThat(missionOf(service.feedOf(userId).missions(), "ATTENDANCE").status())
                .isEqualTo(MissionListResponse.Status.ACHIEVED);
    }

    @Test
    void leavesAttendanceLockedWhenTheUserHasNotOpenedTheAppToday() {
        assertThat(missionOf(service.feedOf(userId).missions(), "ATTENDANCE").claimStatus())
                .isEqualTo(MissionListResponse.ClaimStatus.LOCKED);
    }

    @Test
    void dropsRankMissionsOnDaysTheyCannotBeJudged() {
        // 월요일은 주간 시즌이 초기화되어 어제 순위와 비교할 수 없다. 0/5 로 보여주면 눌러도 반응이 없는 줄 안다.
        assertThat(service.feedOf(userId).missions())
                .extracting(MissionListResponse.Mission::code)
                .doesNotContain("RANK_UP_5");
    }

    @Test
    void includesRankMissionsOnceYesterdaySnapshotCanBeCompared() {
        when(rankUpSignal.rankUpOf(eq(userId), any())).thenReturn(Optional.of(7L));

        assertThat(missionOf(service.feedOf(userId).missions(), "RANK_UP_5"))
                .extracting(MissionListResponse.Mission::current, MissionListResponse.Mission::status)
                .containsExactly(7L, MissionListResponse.Status.ACHIEVED);
    }

    @Test
    void summarisesOnlyTheMissionsShownToday() {
        givenTodayTaps(700);

        MissionListResponse.DailySummary summary = service.feedOf(userId).summary();

        // 오늘 나온 데일리 미션은 출석·TAP_500·TAP_1000 셋이고 그중 둘을 달성했다. 랭킹 미션은 빠졌다.
        assertThat(summary.totalCount()).isEqualTo(3);
        assertThat(summary.completedCount()).isEqualTo(2);
        assertThat(summary.claimableRewardTotal()).isEqualTo(115L);
    }

    @Test
    void showsOneTimeMissionsInTheListButKeepsThemOutOfTheDailySummary() {
        when(notificationOptInSignal.agreed(userId)).thenReturn(true);
        givenTodayTaps(0);

        DailyMissionQueryService.DailyMissionFeed feed = service.feedOf(userId);

        assertThat(feed.missions()).extracting(MissionListResponse.Mission::code).contains("NOTIFICATION_OPT_IN");
        // 단발성 미션을 요약에 넣으면 한 번 받은 보상이 매일 받을 수 있는 것처럼 합계에 남는다.
        assertThat(feed.summary().totalCount()).isEqualTo(3);
        assertThat(feed.summary().completedCount()).isEqualTo(1);
        assertThat(feed.summary().claimableRewardTotal()).isEqualTo(100L);
    }

    @Test
    void decidesTodayInBusinessTimeZoneNotInUtc() {
        // 2026-09-21 16:00Z 는 UTC 로는 21일이지만 KST 로는 22일 새벽 1시다.
        Clock afterKstMidnight = Clock.fixed(Instant.parse("2026-09-21T16:00:00Z"), ZoneOffset.UTC);
        DailyMissionQueryService serviceAfterMidnight = new DailyMissionQueryService(
                catalog, userTapDailyRepository, rankUpSignal, notificationOptInSignal, KST, afterKstMidnight);

        serviceAfterMidnight.feedOf(userId);

        verify(userTapDailyRepository).findByUserIdAndTapDate(userId, LocalDate.of(2026, 9, 22));
    }

    @Test
    void stopsCountingTheAttendanceStreakAtTheLookbackLimit() {
        List<LocalDate> sixtyStraightDays = IntStream.range(0, 60).mapToObj(TODAY::minusDays).toList();
        when(userTapDailyRepository.findRecentTapDates(eq(userId), eq(TODAY), any(Pageable.class)))
                .thenReturn(sixtyStraightDays);

        // 조회 상한이 60일이라 그 이상 연속 출석해도 60 으로 보인다. 스키마에 명시한 동작이다.
        assertThat(service.feedOf(userId).summary().consecutiveAttendanceDays()).isEqualTo(60);
    }

    @Test
    void reportsTheNextMidnightInBusinessTimeZoneAsResetTime() {
        assertThat(service.feedOf(userId).summary().resetAt())
                .isEqualTo(Instant.parse("2026-09-21T15:00:00Z"));
    }

    @Test
    void countsConsecutiveAttendanceBackwardsFromToday() {
        when(userTapDailyRepository.findRecentTapDates(eq(userId), eq(TODAY), any(Pageable.class)))
                .thenReturn(List.of(TODAY, TODAY.minusDays(1), TODAY.minusDays(2), TODAY.minusDays(4)));

        assertThat(service.feedOf(userId).summary().consecutiveAttendanceDays()).isEqualTo(3);
    }

    @Test
    void keepsYesterdayStreakBeforeTodaysFirstVisitIsRecorded() {
        when(userTapDailyRepository.findRecentTapDates(eq(userId), eq(TODAY), any(Pageable.class)))
                .thenReturn(List.of(TODAY.minusDays(1), TODAY.minusDays(2)));

        // 자정 직후에 스트릭이 0으로 보였다가 앱을 켜면 다시 붙는 현상을 막는다.
        assertThat(service.feedOf(userId).summary().consecutiveAttendanceDays()).isEqualTo(2);
    }

    @Test
    void reportsNoStreakWhenTheLastVisitIsOlderThanYesterday() {
        when(userTapDailyRepository.findRecentTapDates(eq(userId), eq(TODAY), any(Pageable.class)))
                .thenReturn(List.of(TODAY.minusDays(3)));

        assertThat(service.feedOf(userId).summary().consecutiveAttendanceDays()).isZero();
    }

    @Test
    void marksDailyMissionsAsInternalPointRewards() {
        assertThat(missionOf(service.feedOf(userId).missions(), "ATTENDANCE"))
                .extracting(MissionListResponse.Mission::rewardType, MissionListResponse.Mission::periodType)
                .containsExactly(MissionListResponse.RewardType.INTERNAL_POINT, MissionListResponse.PeriodType.DAILY);
    }

    private void givenTodayTaps(int totalValidTapCount) {
        UserTapDaily daily = mock(UserTapDaily.class);
        lenient().when(daily.getTotalValidTapCount()).thenReturn(totalValidTapCount);
        when(userTapDailyRepository.findByUserIdAndTapDate(userId, TODAY)).thenReturn(Optional.of(daily));
    }

    private static MissionListResponse.Mission missionOf(List<MissionListResponse.Mission> missions, String code) {
        return missions.stream().filter(mission -> mission.code().equals(code)).findFirst().orElseThrow();
    }

    private static MissionDefinitionView definition(
            String code,
            MissionDefinition.MissionType missionType,
            MissionDefinition.PeriodType periodType,
            long targetValue,
            long rewardPointAmount
    ) {
        return new MissionDefinitionView(code, missionType, periodType, code, null, targetValue, rewardPointAmount, 0);
    }
}
