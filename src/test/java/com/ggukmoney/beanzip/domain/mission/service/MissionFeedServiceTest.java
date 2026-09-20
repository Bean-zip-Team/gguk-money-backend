package com.ggukmoney.beanzip.domain.mission.service;

import com.ggukmoney.beanzip.domain.promotion.dto.response.MissionListResponse;
import com.ggukmoney.beanzip.domain.promotion.service.MissionQueryService;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class MissionFeedServiceTest {

    private final MissionQueryService missionQueryService = mock(MissionQueryService.class);
    private final DailyMissionQueryService dailyMissionQueryService = mock(DailyMissionQueryService.class);
    private final MissionFeedService service = new MissionFeedService(missionQueryService, dailyMissionQueryService);

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

        MissionListResponse response = service.feedOf(userId, true);

        // 화면이 갈라지면 유저는 둘 다 보지 않는다. 상시 미션이 먼저, 데일리가 뒤에 온다.
        assertThat(response.missions())
                .extracting(MissionListResponse.Mission::code)
                .containsExactly("KEYCAP_FIVE_COMPLETE", "ATTENDANCE");
        assertThat(response.daily()).isEqualTo(summary);
    }

    @Test
    void hidesDailyMissionsFromClientsThatDidNotAskForThem() {
        when(missionQueryService.promotionMissionsOf(userId)).thenReturn(List.of(
                mission("KEYCAP_FIVE_COMPLETE", MissionListResponse.RewardType.TOSS_POINT)
        ));

        MissionListResponse response = service.feedOf(userId, false);

        // 구버전 앱은 보상을 전부 토스 포인트로 그린다. 데일리 미션이 섞이면 내부 포인트가
        // 토스 포인트로 표시되고, 달성했는데 지급되지 않는 상태가 화면에 남는다.
        assertThat(response.missions())
                .extracting(MissionListResponse.Mission::code)
                .containsExactly("KEYCAP_FIVE_COMPLETE");
        assertThat(response.daily()).isNull();
        verifyNoInteractions(dailyMissionQueryService);
    }

    private static MissionListResponse.Mission mission(String code, MissionListResponse.RewardType rewardType) {
        return new MissionListResponse.Mission(
                code,
                code,
                null,
                MissionListResponse.PeriodType.DAILY,
                rewardType,
                100L,
                0L,
                1L,
                MissionListResponse.Status.IN_PROGRESS,
                MissionListResponse.ClaimStatus.LOCKED
        );
    }
}
