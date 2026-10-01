package com.ggukmoney.beanzip.domain.mission.service;

import com.ggukmoney.beanzip.domain.mission.dto.response.MissionRewardClaimResponse;
import com.ggukmoney.beanzip.domain.promotion.dto.response.MissionListResponse;
import com.ggukmoney.beanzip.domain.promotion.service.MissionQueryService;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MissionFeedServiceTest {

    private final MissionQueryService missionQueryService = mock(MissionQueryService.class);
    private final DailyMissionQueryService dailyMissionQueryService = mock(DailyMissionQueryService.class);
    private final MissionRewardService missionRewardService = mock(MissionRewardService.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-09-21T05:00:00Z"), ZoneOffset.UTC);
    private final MissionFeedService service =
            new MissionFeedService(missionQueryService, dailyMissionQueryService, missionRewardService, clock);

    private final UUID userId = UUID.randomUUID();

    @Test
    void showsPermanentAndDailyMissionsInOneListWithTodaysSummary() {
        when(missionQueryService.promotionMissionsOf(userId)).thenReturn(List.of(
                mission("KEYCAP_FIVE_COMPLETE", MissionListResponse.RewardType.TOSS_POINT)
        ));
        MissionListResponse.DailySummary summary = new MissionListResponse.DailySummary(
                1, 8, 100L, Instant.parse("2026-09-21T15:00:00Z"), 4);
        when(dailyMissionQueryService.feedOf(userId)).thenReturn(new DailyMissionQueryService.DailyMissionFeed(
                List.of(mission("ATTENDANCE", MissionListResponse.RewardType.INTERNAL_POINT)), summary));

        MissionListResponse response = service.feedOf(userId);

        // 화면이 갈라지면 유저는 둘 다 보지 않는다. 상시 미션이 먼저, 데일리가 뒤에 온다.
        assertThat(response.missions())
                .extracting(MissionListResponse.Mission::code)
                .containsExactly("KEYCAP_FIVE_COMPLETE", "ATTENDANCE");
        assertThat(response.daily()).isEqualTo(summary);
    }

    @Test
    void judgesTodaysMissionsWithTheSameInstantItClaimsWith() {
        UUID rewardId = UUID.randomUUID();
        Instant now = Instant.parse("2026-09-21T05:00:00Z");
        when(missionRewardService.claim(userId, rewardId, now))
                .thenReturn(new MissionRewardService.ClaimResult(1, 15L, 1380L));

        MissionRewardClaimResponse response = service.claim(userId, rewardId);

        // 시각을 두 번 재면 자정 경계에서 방금 만든 보상을 곧바로 소멸로 판정한다.
        verify(dailyMissionQueryService).materializeRewards(userId, now);
        assertThat(response).isEqualTo(new MissionRewardClaimResponse(1, 15L, 1380L));
    }

    @Test
    void reportsHowMuchTheBulkClaimActuallyPaid() {
        Instant now = Instant.parse("2026-09-21T05:00:00Z");
        when(missionRewardService.claimAll(userId, now))
                .thenReturn(new MissionRewardService.ClaimResult(2, 115L, 1495L));

        assertThat(service.claimAll(userId))
                .isEqualTo(new MissionRewardClaimResponse(2, 115L, 1495L));
        verify(dailyMissionQueryService).materializeRewards(userId, now);
    }

    private static MissionListResponse.Mission mission(String code, MissionListResponse.RewardType rewardType) {
        return new MissionListResponse.Mission(
                code,
                code,
                null,
                null,
                MissionListResponse.PeriodType.DAILY,
                rewardType,
                100L,
                0L,
                1L,
                MissionListResponse.Status.IN_PROGRESS,
                MissionListResponse.ClaimStatus.LOCKED,
                null
        );
    }
}
