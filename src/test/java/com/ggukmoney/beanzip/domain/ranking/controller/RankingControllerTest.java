package com.ggukmoney.beanzip.domain.ranking.controller;

import com.ggukmoney.beanzip.domain.ranking.dto.response.CurrentRankingResponse;
import com.ggukmoney.beanzip.domain.ranking.dto.response.MyRankingResponse;
import com.ggukmoney.beanzip.domain.ranking.dto.response.RankingHistoryItemResponse;
import com.ggukmoney.beanzip.domain.ranking.dto.response.RankingHistoryResponse;
import com.ggukmoney.beanzip.domain.ranking.dto.response.RankingItemResponse;
import com.ggukmoney.beanzip.domain.ranking.dto.response.RankingSeasonResponse;
import com.ggukmoney.beanzip.domain.ranking.service.RankingHistoryService;
import com.ggukmoney.beanzip.domain.ranking.service.RankingQueryService;
import com.ggukmoney.beanzip.global.common.GlobalExceptionHandler;
import com.ggukmoney.beanzip.global.config.OpenApiConfig;
import com.ggukmoney.beanzip.global.interceptor.AuthRequestAttributes;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RankingControllerTest {

    private final RankingQueryService rankingQueryService = mock(RankingQueryService.class);
    private final RankingHistoryService rankingHistoryService = mock(RankingHistoryService.class);
    private final RankingController rankingController = new RankingController(rankingQueryService, rankingHistoryService);
    private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(rankingController)
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

    @Test
    void rankingApiHasOpenApiDocumentation() throws Exception {
        Tag tag = RankingController.class.getAnnotation(Tag.class);
        assertThat(tag).isNotNull();
        assertThat(tag.name()).isEqualTo("Rankings");

        SecurityRequirement securityRequirement = RankingController.class.getAnnotation(SecurityRequirement.class);
        assertThat(securityRequirement).isNotNull();
        assertThat(securityRequirement.name()).isEqualTo(OpenApiConfig.BEARER_AUTH);

        Method method = RankingController.class.getMethod("getCurrentRanking", HttpServletRequest.class, Integer.class);
        Operation operation = method.getAnnotation(Operation.class);
        assertThat(operation).isNotNull();

        ApiResponses responses = method.getAnnotation(ApiResponses.class);
        assertThat(responses).isNotNull();
        assertThat(Stream.of(responses.value())
                .map(io.swagger.v3.oas.annotations.responses.ApiResponse::responseCode))
                .containsExactly("200", "400", "401");

        Parameter requestParameter = method.getParameters()[0].getAnnotation(Parameter.class);
        assertThat(requestParameter.hidden()).isTrue();

        Parameter limitParameter = method.getParameters()[1].getAnnotation(Parameter.class);
        assertThat(limitParameter.description()).contains("100");

        Method historyMethod = RankingController.class.getMethod(
                "getRankingHistory",
                HttpServletRequest.class,
                String.class,
                Integer.class
        );
        Operation historyOperation = historyMethod.getAnnotation(Operation.class);
        assertThat(historyOperation).isNotNull();
        assertThat(historyMethod.getAnnotation(GetMapping.class).value()).containsExactly("/history");
        Parameter cursorParameter = historyMethod.getParameters()[1].getAnnotation(Parameter.class);
        Parameter sizeParameter = historyMethod.getParameters()[2].getAnnotation(Parameter.class);
        assertThat(cursorParameter.description()).contains("cursor");
        assertThat(sizeParameter.description()).contains("100");
    }

    @Test
    void rankingResponseDtosHaveOpenApiSchemas() {
        assertThat(CurrentRankingResponse.class.getAnnotation(Schema.class).description())
                .isEqualTo("현재 랭킹 응답");
        assertThat(RankingItemResponse.class.getAnnotation(Schema.class).description())
                .isEqualTo("랭킹 목록 항목");
        assertThat(MyRankingResponse.class.getAnnotation(Schema.class).description())
                .isEqualTo("내 랭킹 정보");
        assertThat(RankingSeasonResponse.class.getAnnotation(Schema.class).description())
                .isEqualTo("현재 랭킹 시즌 정보");

        assertThat(schemaDescription(CurrentRankingResponse.class, "season"))
                .isEqualTo("현재 랭킹 시즌 정보");
        assertThat(schemaDescription(CurrentRankingResponse.class, "totalParticipantCount"))
                .isEqualTo("전체 랭킹 참가자 수");
        assertThat(schemaDescription(RankingItemResponse.class, "isMe"))
                .isEqualTo("현재 로그인한 사용자인지 여부");
        assertThat(schemaDescription(RankingItemResponse.class, "rankChange"))
                .isEqualTo("순위 변화. previousRank - rank");
        assertThat(schemaDescription(MyRankingResponse.class, "previousRank"))
                .isEqualTo("직전 주 최종 순위. 미참가 시 null");
        assertThat(schemaDescription(MyRankingResponse.class, "scoreGapToFirst"))
                .isEqualTo("1위와 점수 차이");
    }

    @Test
    void rankingHistoryResponseDtosHaveOpenApiSchemas() {
        assertThat(RankingHistoryResponse.class.getAnnotation(Schema.class).description())
                .isEqualTo("Ranking history page response");
        assertThat(RankingHistoryItemResponse.class.getAnnotation(Schema.class).description())
                .isEqualTo("Ranking history item");
        assertThat(schemaDescription(RankingHistoryItemResponse.class, "endsAt"))
                .isEqualTo("Season end instant");
        assertThat(schemaDescription(RankingHistoryResponse.class, "nextCursor"))
                .isEqualTo("Next page cursor");
    }

    @Test
    void getCurrentRankingUsesAuthenticatedUserAndDefaultLimit() throws Exception {
        UUID userId = UUID.randomUUID();
        when(rankingQueryService.getCurrentRanking(userId, null)).thenReturn(emptyResponse());

        mockMvc.perform(get("/api/rankings/current").requestAttr(AuthRequestAttributes.USER_ID, userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalParticipantCount").value(0))
                .andExpect(jsonPath("$.data.season.timeZone").value("Asia/Seoul"))
                .andExpect(jsonPath("$.error").doesNotExist());

        verify(rankingQueryService).getCurrentRanking(userId, null);
    }

    @Test
    void getCurrentRankingAcceptsLimit() throws Exception {
        UUID userId = UUID.randomUUID();
        when(rankingQueryService.getCurrentRanking(userId, 100)).thenReturn(emptyResponse());

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

    @Test
    void getRankingHistoryUsesAuthenticatedUserCursorAndSize() throws Exception {
        UUID userId = UUID.randomUUID();
        RankingHistoryResponse response = new RankingHistoryResponse(
                List.of(new RankingHistoryItemResponse(
                        "WEEKLY_20260720",
                        Instant.parse("2026-07-19T15:00:00Z"),
                        Instant.parse("2026-07-26T15:00:00Z"),
                        7L,
                        950L
                )),
                "next-cursor",
                true
        );
        when(rankingHistoryService.getHistory(userId, "cursor-1", 20)).thenReturn(response);

        mockMvc.perform(get("/api/rankings/history")
                        .requestAttr(AuthRequestAttributes.USER_ID, userId)
                        .param("cursor", "cursor-1")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content[0].seasonCode").value("WEEKLY_20260720"))
                .andExpect(jsonPath("$.data.content[0].endsAt").value("2026-07-26T15:00:00Z"))
                .andExpect(jsonPath("$.data.content[0].myFinalRank").value(7))
                .andExpect(jsonPath("$.data.content[0].myFinalScore").value(950))
                .andExpect(jsonPath("$.data.nextCursor").value("next-cursor"))
                .andExpect(jsonPath("$.data.hasNext").value(true));

        verify(rankingHistoryService).getHistory(userId, "cursor-1", 20);
    }

    @Test
    void getRankingHistoryRequiresAccessJwtUser() throws Exception {
        mockMvc.perform(get("/api/rankings/history"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_REQUIRED"));
    }

    private String schemaDescription(Class<?> type, String componentName) {
        return Stream.of(type.getRecordComponents())
                .filter(component -> component.getName().equals(componentName))
                .findFirst()
                .map(RecordComponent::getAccessor)
                .map(accessor -> accessor.getAnnotation(Schema.class))
                .map(Schema::description)
                .orElseThrow();
    }

    private CurrentRankingResponse emptyResponse() {
        return new CurrentRankingResponse(
                new RankingSeasonResponse(
                        Instant.parse("2026-07-19T15:00:00Z"),
                        Instant.parse("2026-07-26T15:00:00Z"),
                        Instant.parse("2026-07-26T15:00:00Z"),
                        "MONDAY",
                        "00:00",
                        "Asia/Seoul"
                ),
                List.of(),
                new MyRankingResponse(null, null, null, 0L, 0L),
                0L
        );
    }
}
