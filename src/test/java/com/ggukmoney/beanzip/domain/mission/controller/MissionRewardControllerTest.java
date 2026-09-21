package com.ggukmoney.beanzip.domain.mission.controller;

import com.ggukmoney.beanzip.domain.mission.dto.response.MissionRewardClaimResponse;
import com.ggukmoney.beanzip.domain.mission.dto.response.MissionRewardListResponse;
import com.ggukmoney.beanzip.domain.mission.service.MissionFeedService;
import com.ggukmoney.beanzip.global.common.GlobalExceptionHandler;
import com.ggukmoney.beanzip.global.interceptor.AuthRequestAttributes;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MissionRewardControllerTest {

    private final MissionFeedService missionFeedService = mock(MissionFeedService.class);
    private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new MissionRewardController(missionFeedService))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

    private final UUID userId = UUID.fromString("00000000-0000-0000-0000-000000000042");
    private final UUID rewardId = UUID.fromString("00000000-0000-0000-0000-0000000000aa");

    @Test
    void listsClaimableRewardsWhenNoStatusIsGiven() throws Exception {
        when(missionFeedService.rewardHistory(userId, MissionRewardListResponse.RewardStatus.CLAIMABLE))
                .thenReturn(new MissionRewardListResponse(List.of(reward()), 20L));

        mockMvc.perform(get("/api/missions/rewards").requestAttr(AuthRequestAttributes.USER_ID, userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rewards[0].missionCode").value("TAP_1000"))
                .andExpect(jsonPath("$.data.rewards[0].status").value("CLAIMABLE"))
                .andExpect(jsonPath("$.data.totalPointAmount").value(20));
    }

    @Test
    void listsWhatTheUserMissedWhenAskedForExpiredRewards() throws Exception {
        when(missionFeedService.rewardHistory(userId, MissionRewardListResponse.RewardStatus.EXPIRED))
                .thenReturn(new MissionRewardListResponse(List.of(), 0L));

        mockMvc.perform(get("/api/missions/rewards")
                        .param("status", "EXPIRED")
                        .requestAttr(AuthRequestAttributes.USER_ID, userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalPointAmount").value(0));
    }

    @Test
    void reportsAlreadyClaimedRewardsAsConflict() throws Exception {
        when(missionFeedService.claim(eq(userId), any()))
                .thenThrow(new ResponseStatusException(HttpStatus.CONFLICT, "MISSION_REWARD_ALREADY_CLAIMED"));

        mockMvc.perform(post("/api/missions/rewards/{rewardId}/claim", rewardId)
                        .requestAttr(AuthRequestAttributes.USER_ID, userId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("MISSION_REWARD_ALREADY_CLAIMED"));
    }

    @Test
    void reportsRewardsThatPassedMidnightAsGone() throws Exception {
        when(missionFeedService.claim(eq(userId), any()))
                .thenThrow(new ResponseStatusException(HttpStatus.GONE, "MISSION_REWARD_EXPIRED"));

        mockMvc.perform(post("/api/missions/rewards/{rewardId}/claim", rewardId)
                        .requestAttr(AuthRequestAttributes.USER_ID, userId))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.error.code").value("MISSION_REWARD_EXPIRED"));
    }

    @Test
    void returnsWhatTheBulkClaimPaidAndTheBalanceAfterwards() throws Exception {
        when(missionFeedService.claimAll(userId)).thenReturn(new MissionRewardClaimResponse(2, 115L, 1495L));

        mockMvc.perform(post("/api/missions/rewards/claim-all")
                        .requestAttr(AuthRequestAttributes.USER_ID, userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.claimedCount").value(2))
                .andExpect(jsonPath("$.data.claimedPointAmount").value(115))
                .andExpect(jsonPath("$.data.pointBalance").value(1495));
    }

    @Test
    void rejectsUnauthenticatedRequests() throws Exception {
        mockMvc.perform(post("/api/missions/rewards/claim-all"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_REQUIRED"));
    }

    private static MissionRewardListResponse.Reward reward() {
        return new MissionRewardListResponse.Reward(
                UUID.fromString("00000000-0000-0000-0000-0000000000aa"),
                "TAP_1000",
                "2026-09-21",
                20L,
                MissionRewardListResponse.RewardStatus.CLAIMABLE,
                Instant.parse("2026-09-21T05:00:00Z"),
                Instant.parse("2026-09-21T15:00:00Z"),
                null
        );
    }
}
