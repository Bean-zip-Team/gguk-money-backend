package com.ggukmoney.beanzip.domain.ranking.controller;

import com.ggukmoney.beanzip.domain.ranking.dto.response.CurrentRankingResponse;
import com.ggukmoney.beanzip.domain.ranking.dto.response.MyRankingResponse;
import com.ggukmoney.beanzip.domain.ranking.service.RankingQueryService;
import com.ggukmoney.beanzip.global.common.GlobalExceptionHandler;
import com.ggukmoney.beanzip.global.interceptor.AuthRequestAttributes;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RankingControllerTest {

    private final RankingQueryService rankingQueryService = mock(RankingQueryService.class);
    private final RankingController rankingController = new RankingController(rankingQueryService);
    private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(rankingController)
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

    @Test
    void getCurrentRankingUsesAuthenticatedUserAndDefaultLimit() throws Exception {
        UUID userId = UUID.randomUUID();
        when(rankingQueryService.getCurrentRanking(userId, null))
                .thenReturn(new CurrentRankingResponse(List.of(), new MyRankingResponse(null, 0L, 0L), 0L));

        mockMvc.perform(get("/api/rankings/current").requestAttr(AuthRequestAttributes.USER_ID, userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalParticipantCount").value(0))
                .andExpect(jsonPath("$.error").doesNotExist());

        verify(rankingQueryService).getCurrentRanking(userId, null);
    }

    @Test
    void getCurrentRankingAcceptsLimit() throws Exception {
        UUID userId = UUID.randomUUID();
        when(rankingQueryService.getCurrentRanking(userId, 100))
                .thenReturn(new CurrentRankingResponse(List.of(), new MyRankingResponse(null, 0L, 0L), 0L));

        mockMvc.perform(get("/api/rankings/current")
                        .requestAttr(AuthRequestAttributes.USER_ID, userId)
                        .param("limit", "100"))
                .andExpect(status().isOk());

        verify(rankingQueryService).getCurrentRanking(userId, 100);
    }

    @Test
    void getCurrentRankingRequiresAccessJwtUser() throws Exception {
        mockMvc.perform(get("/api/rankings/current"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_REQUIRED"));
    }
}
