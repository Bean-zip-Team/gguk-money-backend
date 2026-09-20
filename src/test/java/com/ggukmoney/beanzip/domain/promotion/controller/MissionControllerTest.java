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
    void asksForPermanentMissionsOnlyWhenTheClientDoesNotOptIntoDailyMissions() throws Exception {
        when(missionFeedService.feedOf(eq(userId), eq(false)))
                .thenReturn(new MissionListResponse(List.of(mission("KEYCAP_FIVE_COMPLETE")), null));

        // 구버전 앱은 파라미터를 보내지 않는다. 기본값이 false 라야 데일리 미션이 섞이지 않는다.
        mockMvc.perform(get("/api/missions").requestAttr(AuthRequestAttributes.USER_ID, userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.missions[0].code").value("KEYCAP_FIVE_COMPLETE"))
                .andExpect(jsonPath("$.data.daily").doesNotExist());
    }

    @Test
    void returnsDailyMissionsAndTodaySummaryWhenTheClientOptsIn() throws Exception {
        when(missionFeedService.feedOf(eq(userId), eq(true))).thenReturn(new MissionListResponse(
                List.of(mission("KEYCAP_FIVE_COMPLETE"), mission("ATTENDANCE")),
                new MissionListResponse.DailySummary(1, 6, 100L, Instant.parse("2026-09-21T15:00:00Z"), 4)
        ));

        mockMvc.perform(get("/api/missions")
                        .param("includeDaily", "true")
                        .requestAttr(AuthRequestAttributes.USER_ID, userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.missions[1].code").value("ATTENDANCE"))
                .andExpect(jsonPath("$.data.daily.totalCount").value(6))
                .andExpect(jsonPath("$.data.daily.consecutiveAttendanceDays").value(4));
    }

    @Test
    void rejectsUnauthenticatedRequests() throws Exception {
        mockMvc.perform(get("/api/missions"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_REQUIRED"));
    }

    private static MissionListResponse.Mission mission(String code) {
        return new MissionListResponse.Mission(
                code,
                code,
                null,
                MissionListResponse.PeriodType.DAILY,
                MissionListResponse.RewardType.INTERNAL_POINT,
                100L,
                0L,
                1L,
                MissionListResponse.Status.IN_PROGRESS,
                MissionListResponse.ClaimStatus.LOCKED
        );
    }
}
