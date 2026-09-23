package com.ggukmoney.beanzip.domain.mission.service;

import com.ggukmoney.beanzip.domain.mission.entity.MissionDefinition;
import com.ggukmoney.beanzip.domain.mission.entity.MissionReward;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
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

    private final MissionRewardService missionRewardService = mock(MissionRewardService.class);

    private final DailyMissionQueryService service = new DailyMissionQueryService(
            catalog, missionRewardService, userTapDailyRepository, rankUpSignal, notificationOptInSignal, KST, clock);

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
        lenient().when(notificationOptInSignal.agreed(userId)).thenReturn(Optional.of(false));
        lenient().when(userTapDailyRepository.findRecentTapDates(eq(userId), eq(TODAY), any(Pageable.class)))
                .thenReturn(List.of());
        lenient().when(missionRewardService.rewardsOf(eq(userId), anyList())).thenReturn(Map.of());
        // 실제 서비스처럼 달성한 미션마다 수령 대기 보상을 만들어 돌려준다.
        lenient().when(missionRewardService.createMissing(eq(userId), anyList(), anyMap(), any(Instant.class)))
                .thenAnswer(invocation -> {
                    List<MissionRewardService.AchievedMission> achieved = invocation.getArgument(1);
                    Map<MissionRewardService.RewardKey, MissionReward> existing = invocation.getArgument(2);
                    Map<MissionRewardService.RewardKey, MissionReward> created = new LinkedHashMap<>();
                    achieved.stream()
                            .filter(mission -> !existing.containsKey(rewardKey(mission)))
                            .forEach(mission -> created.put(rewardKey(mission), MissionReward.claimable(
                                    userId,
                                    mission.missionCode(),
                                    mission.periodKey(),
                                    mission.rewardPointAmount(),
                                    Instant.parse("2026-09-21T05:00:00Z"),
                                    mission.expiresAt()
                            )));
                    return created;
                });
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
        when(notificationOptInSignal.agreed(userId)).thenReturn(Optional.of(true));
        givenTodayTaps(0);

        DailyMissionQueryService.DailyMissionFeed feed = service.feedOf(userId);

        assertThat(feed.missions()).extracting(MissionListResponse.Mission::code).contains("NOTIFICATION_OPT_IN");
        // 진행도 게이지는 매일 반복되는 미션만 센다. 단발성 미션까지 넣으면 한 번 받고 사라질 미션이
        // 오늘의 진행도를 계속 부풀린다.
        assertThat(feed.summary().totalCount()).isEqualTo(3);
        assertThat(feed.summary().completedCount()).isEqualTo(1);
        // 반면 받을 보상 합계에는 들어간다. 일괄 수령이 단발성 보상까지 지급하기 때문이다.
        assertThat(feed.summary().claimableRewardTotal()).isEqualTo(200L);
    }

    @Test
    void hidesTheOptInMissionWhenThereIsNoWayToAgree() {
        // 동의 토글이 내려가지 않는 상태다. 그대로 두면 켤 방법이 없는 미션을 계속 보여 주게 된다.
        when(notificationOptInSignal.agreed(userId)).thenReturn(Optional.empty());

        assertThat(service.feedOf(userId).missions())
                .extracting(MissionListResponse.Mission::code)
                .doesNotContain("NOTIFICATION_OPT_IN");
    }

    @Test
    void marksAchievedMissionsClaimableWithTheRewardIdentifierToClaimWith() {
        givenTodayTaps(700);

        MissionListResponse.Mission tapMission = missionOf(service.feedOf(userId).missions(), "TAP_500");

        assertThat(tapMission.claimStatus()).isEqualTo(MissionListResponse.ClaimStatus.CLAIMABLE);
        assertThat(tapMission.rewardId()).isNotNull();
    }

    @Test
    void hidesAOneTimeMissionOnceItsRewardWasClaimed() {
        when(notificationOptInSignal.agreed(userId)).thenReturn(Optional.of(true));
        when(missionRewardService.rewardsOf(eq(userId), anyList()))
                .thenReturn(Map.of(
                        new MissionRewardService.RewardKey("NOTIFICATION_OPT_IN", MissionReward.ONE_TIME_PERIOD_KEY),
                        claimedReward("NOTIFICATION_OPT_IN", MissionReward.ONE_TIME_PERIOD_KEY, 100)));

        // 한 번 수행한 유저에게는 다시 뜨지 않는다. 나중에 알림을 꺼도 마찬가지다.
        assertThat(service.feedOf(userId).missions())
                .extracting(MissionListResponse.Mission::code)
                .doesNotContain("NOTIFICATION_OPT_IN");
    }

    @Test
    void keepsARewardClaimableAfterTheConditionStopsHolding() {
        // 알림을 허용해 보상이 생긴 뒤 다시 껐다. 이미 달성한 보상은 받을 수 있어야 한다.
        when(notificationOptInSignal.agreed(userId)).thenReturn(Optional.of(false));
        when(missionRewardService.rewardsOf(eq(userId), anyList()))
                .thenReturn(Map.of(
                        new MissionRewardService.RewardKey("NOTIFICATION_OPT_IN", MissionReward.ONE_TIME_PERIOD_KEY),
                        MissionReward.claimable(userId, "NOTIFICATION_OPT_IN", MissionReward.ONE_TIME_PERIOD_KEY, 100,
                                Instant.parse("2026-09-20T05:00:00Z"), null)));

        assertThat(missionOf(service.feedOf(userId).missions(), "NOTIFICATION_OPT_IN"))
                .extracting(MissionListResponse.Mission::status, MissionListResponse.Mission::claimStatus)
                .containsExactly(MissionListResponse.Status.ACHIEVED, MissionListResponse.ClaimStatus.CLAIMABLE);
    }

    @Test
    void keepsClaimedRewardsOutOfTheClaimableTotal() {
        givenTodayTaps(700);
        when(missionRewardService.rewardsOf(eq(userId), anyList()))
                .thenReturn(Map.of(
                        new MissionRewardService.RewardKey("TAP_500", TODAY.toString()),
                        claimedReward("TAP_500", TODAY.toString(), 15)));

        DailyMissionQueryService.DailyMissionFeed feed = service.feedOf(userId);

        // 이미 받은 15P 는 "받을 보상"에서 빠지고, 출석 100P 만 남는다.
        assertThat(feed.summary().claimableRewardTotal()).isEqualTo(100L);
        assertThat(missionOf(feed.missions(), "TAP_500"))
                .extracting(MissionListResponse.Mission::status, MissionListResponse.Mission::claimStatus)
                .containsExactly(MissionListResponse.Status.REWARDED, MissionListResponse.ClaimStatus.CLAIMED);
    }

    @Test
    void decidesTodayInBusinessTimeZoneNotInUtc() {
        // 2026-09-21 16:00Z 는 UTC 로는 21일이지만 KST 로는 22일 새벽 1시다.
        Clock afterKstMidnight = Clock.fixed(Instant.parse("2026-09-21T16:00:00Z"), ZoneOffset.UTC);
        DailyMissionQueryService serviceAfterMidnight = new DailyMissionQueryService(
                catalog, missionRewardService, userTapDailyRepository, rankUpSignal, notificationOptInSignal, KST,
                afterKstMidnight);

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

    private static MissionRewardService.RewardKey rewardKey(MissionRewardService.AchievedMission mission) {
        return new MissionRewardService.RewardKey(mission.missionCode(), mission.periodKey());
    }

    private MissionReward claimedReward(String missionCode, String periodKey, long rewardPointAmount) {
        MissionReward reward = MissionReward.claimable(
                userId, missionCode, periodKey, rewardPointAmount, Instant.parse("2026-09-21T04:00:00Z"), null);
        reward.claim(Instant.parse("2026-09-21T04:30:00Z"));
        return reward;
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
