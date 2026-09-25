package com.ggukmoney.beanzip.domain.promotion.controller;

import com.ggukmoney.beanzip.domain.mission.service.MissionFeedService;
import com.ggukmoney.beanzip.domain.promotion.dto.response.MissionListResponse;
import com.ggukmoney.beanzip.global.common.GlobalExceptionHandler;
import com.ggukmoney.beanzip.global.interceptor.AuthRequestAttributes;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MissionControllerTest {

    private final MissionFeedService missionFeedService = mock(MissionFeedService.class);
    private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new MissionController(missionFeedService))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

    private final UUID userId = UUID.fromString("00000000-0000-0000-0000-000000000042");

    @Test
    void returnsPermanentAndDailyMissionsWithTodaySummary() throws Exception {
        when(missionFeedService.feedOf(eq(userId))).thenReturn(new MissionListResponse(
                List.of(
                        mission("KEYCAP_FIVE_COMPLETE", null),
                        mission("ATTENDANCE", MissionListResponse.MissionType.ATTENDANCE)
                ),
                new MissionListResponse.DailySummary(1, 6, 100L, Instant.parse("2026-09-21T15:00:00Z"), 4)
        ));

        // 파라미터 없이 두 종류가 한 목록으로 온다. 스펙이 정한 계약이다.
        mockMvc.perform(get("/api/missions").requestAttr(AuthRequestAttributes.USER_ID, userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.missions[1].code").value("ATTENDANCE"))
                .andExpect(jsonPath("$.data.daily.totalCount").value(6))
                .andExpect(jsonPath("$.data.daily.consecutiveAttendanceDays").value(4));
    }

    @Test
    void exposesMissionTypeSoTheScreenDoesNotGuessItFromTheCode() throws Exception {
        when(missionFeedService.feedOf(eq(userId))).thenReturn(new MissionListResponse(
                List.of(
                        mission("KEYCAP_FIVE_COMPLETE", null),
                        mission("NOTIFICATION_OPT_IN", MissionListResponse.MissionType.NOTIFICATION_OPT_IN)
                ),
                new MissionListResponse.DailySummary(0, 6, 0L, Instant.parse("2026-09-21T15:00:00Z"), 0)
        ));

        // 화면이 종류마다 다르게 그린다. code 문자열 규칙에 의존하면 코드명을 바꿀 때 화면이 깨진다.
        mockMvc.perform(get("/api/missions").requestAttr(AuthRequestAttributes.USER_ID, userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.missions[1].missionType").value("NOTIFICATION_OPT_IN"))
                // 상시 미션은 프로모션 기반이라 이 분류를 갖지 않는다.
                .andExpect(jsonPath("$.data.missions[0].missionType").doesNotExist());
    }

    @Test
    void rejectsUnauthenticatedRequests() throws Exception {
        mockMvc.perform(get("/api/missions"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_REQUIRED"));
    }

    private static MissionListResponse.Mission mission(String code, MissionListResponse.MissionType missionType) {
        return new MissionListResponse.Mission(
                code,
                code,
                null,
                missionType,
                MissionListResponse.PeriodType.DAILY,
                MissionListResponse.RewardType.INTERNAL_POINT,
                100L,
                0L,
                1L,
                MissionListResponse.Status.IN_PROGRESS,
                MissionListResponse.ClaimStatus.LOCKED,
                null
        );
    }
}
