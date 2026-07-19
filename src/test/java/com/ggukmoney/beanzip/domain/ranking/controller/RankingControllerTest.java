package com.ggukmoney.beanzip.domain.ranking.controller;

import com.ggukmoney.beanzip.domain.ranking.dto.response.CurrentRankingResponse;
import com.ggukmoney.beanzip.domain.ranking.dto.response.MyRankingResponse;
import com.ggukmoney.beanzip.domain.ranking.dto.response.RankingItemResponse;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
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
    private final RankingController rankingController = new RankingController(rankingQueryService);
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
        assertThat(operation.summary()).isEqualTo("현재 전체 랭킹 조회");

        ApiResponses responses = method.getAnnotation(ApiResponses.class);
        assertThat(responses).isNotNull();
        assertThat(Stream.of(responses.value())
                .map(io.swagger.v3.oas.annotations.responses.ApiResponse::responseCode))
                .containsExactly("200", "400", "401");

        Parameter requestParameter = method.getParameters()[0].getAnnotation(Parameter.class);
        assertThat(requestParameter.hidden()).isTrue();

        Parameter limitParameter = method.getParameters()[1].getAnnotation(Parameter.class);
        assertThat(limitParameter.description()).contains("조회할 상위 랭킹 개수");
    }

    @Test
    void rankingResponseDtosHaveOpenApiSchemas() {
        assertThat(CurrentRankingResponse.class.getAnnotation(Schema.class).description())
                .isEqualTo("현재 전체 랭킹 응답");
        assertThat(RankingItemResponse.class.getAnnotation(Schema.class).description())
                .isEqualTo("랭킹 목록 항목");
        assertThat(MyRankingResponse.class.getAnnotation(Schema.class).description())
                .isEqualTo("내 랭킹 정보");

        assertThat(schemaDescription(CurrentRankingResponse.class, "totalParticipantCount"))
                .isEqualTo("전체 랭킹 참가자 수");
        assertThat(schemaDescription(RankingItemResponse.class, "isMe"))
                .isEqualTo("현재 로그인한 사용자인지 여부");
        assertThat(schemaDescription(MyRankingResponse.class, "scoreGapToFirst"))
                .isEqualTo("1위와의 점수 차이");
    }

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

    private String schemaDescription(Class<?> type, String componentName) {
        return Stream.of(type.getRecordComponents())
                .filter(component -> component.getName().equals(componentName))
                .findFirst()
                .map(RecordComponent::getAccessor)
                .map(accessor -> accessor.getAnnotation(Schema.class))
                .map(Schema::description)
                .orElseThrow();
    }
}
