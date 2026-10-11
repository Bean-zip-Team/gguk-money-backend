package com.ggukmoney.beanzip.domain.keycap.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ggukmoney.beanzip.domain.auth.service.AuthService;
import com.ggukmoney.beanzip.domain.auth.service.JwtTokenProvider;
import com.ggukmoney.beanzip.domain.keycap.dto.response.KeycapBoxHistoryItemResponse;
import com.ggukmoney.beanzip.domain.keycap.dto.response.KeycapBoxHistoryResponse;
import com.ggukmoney.beanzip.domain.keycap.dto.response.KeycapBoxStatusResponse;
import com.ggukmoney.beanzip.domain.keycap.service.KeycapBoxQueryService;
import com.ggukmoney.beanzip.global.common.GlobalExceptionHandler;
import com.ggukmoney.beanzip.global.interceptor.AuthInterceptor;
import com.ggukmoney.beanzip.global.interceptor.AuthRequestAttributes;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class KeycapBoxControllerTest {

    private final AuthService authService = mock(AuthService.class);
    private final KeycapBoxQueryService keycapBoxQueryService = mock(KeycapBoxQueryService.class);
    private final KeycapBoxController keycapBoxController = new KeycapBoxController(keycapBoxQueryService);
    private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(keycapBoxController)
            .addInterceptors(new AuthInterceptor(authService))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

    @Test
    void getStatusPassesAuthenticatedUserIdToService() {
        UUID userId = UUID.randomUUID();
        KeycapBoxStatusResponse response = KeycapBoxStatusResponse.of(12, 5, 45, 100);
        when(keycapBoxQueryService.getStatus(userId)).thenReturn(response);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(AuthRequestAttributes.USER_ID, userId);

        var result = keycapBoxController.getStatus(request);

        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().data()).isEqualTo(response);
        verify(keycapBoxQueryService).getStatus(userId);
    }

    @Test
    void authenticatedGetStatusReturnsShardFieldsAndNeutralBoxFields() throws Exception {
        stubAuthenticatedAccessToken("access-token");
        when(keycapBoxQueryService.getStatus(authenticatedUserId()))
                .thenReturn(KeycapBoxStatusResponse.of(12, 5, 45, 100));

        var result = mockMvc.perform(get("/api/keycap-boxes/status")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer access-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.shardBalance").value(12))
                .andExpect(jsonPath("$.data.drawPrice").value(5))
                .andExpect(jsonPath("$.data.canDraw").value(true))
                .andExpect(jsonPath("$.data.shardProgressTapCount").value(45))
                .andExpect(jsonPath("$.data.nextShardRequiredTapCount").value(100))
                // 구버전 앱이 읽는 상자 필드는 이름을 유지하고 중립값을 준다.
                .andExpect(jsonPath("$.data.boxBalance").value(0))
                .andExpect(jsonPath("$.data.canFreeOpen").value(false))
                .andExpect(jsonPath("$.data.canAdOpen").value(false))
                .andExpect(jsonPath("$.data.charging").value(false))
                .andExpect(jsonPath("$.data.boxProgressTapCount").value(45))
                .andExpect(jsonPath("$.data.nextBoxRequiredTapCount").value(100))
                .andExpect(jsonPath("$.data.id").doesNotExist())
                .andExpect(jsonPath("$.data.publicId").doesNotExist())
                .andExpect(jsonPath("$.error").doesNotExist())
                .andReturn();

        JsonNode data = new ObjectMapper().readTree(result.getResponse().getContentAsString()).path("data");
        assertThat(data.has("nextRechargeAt")).isTrue();
        assertThat(data.get("nextRechargeAt").isNull()).isTrue();
    }

    @Test
    void unauthenticatedGetStatusIsRejectedByExistingAuthPolicy() throws Exception {
        mockMvc.perform(get("/api/keycap-boxes/status"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("AUTH_REQUIRED"));
    }

    @Test
    void missingTapProgressUsesExistingTapProgressErrorCode() throws Exception {
        stubAuthenticatedAccessToken("access-token");
        when(keycapBoxQueryService.getStatus(authenticatedUserId()))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "TAP_PROGRESS_NOT_FOUND"));

        mockMvc.perform(get("/api/keycap-boxes/status")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer access-token"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("TAP_PROGRESS_NOT_FOUND"));
    }

    @Test
    void boxOpenEndpointsAreGone() {
        // 개봉·일괄 개봉은 BEA-329 로 사라졌다. 뽑기는 /api/keycaps/draw 다.
        assertThat(KeycapBoxController.class.getDeclaredMethods())
                .extracting(Method::getName)
                .doesNotContain("open", "bulkOpen")
                .contains("getStatus", "getHistory");
    }

    @Test
    void statusSwaggerDescribesShardWalletNotBoxCycle() throws Exception {
        Method method = KeycapBoxController.class.getDeclaredMethod("getStatus", jakarta.servlet.http.HttpServletRequest.class);
        ApiResponses responses = method.getAnnotation(ApiResponses.class);
        ApiResponse success = java.util.Arrays.stream(responses.value())
                .filter(response -> response.responseCode().equals("200"))
                .findFirst()
                .orElseThrow();
        Content content = success.content()[0];
        Schema shardBalance = KeycapBoxStatusResponse.class.getRecordComponents()[0]
                .getAccessor()
                .getAnnotation(Schema.class);

        assertThat(content.examples()).extracting(io.swagger.v3.oas.annotations.media.ExampleObject::name)
                .containsExactly("조각 12개 보유");
        assertThat(content.examples()[0].value()).contains("\"shardBalance\": 12").contains("\"drawPrice\": 5");
        assertThat(shardBalance.description()).contains("조각");
    }

    @Test
    void authenticatedHistoryReturnsCursorPageWithoutInternalFields() throws Exception {
        stubAuthenticatedAccessToken("access-token");
        UUID boxOpenId = UUID.randomUUID();
        UUID keycapId = UUID.randomUUID();
        Instant openedAt = Instant.parse("2026-07-15T00:00:00Z");
        KeycapBoxHistoryResponse response = new KeycapBoxHistoryResponse(
                List.of(new KeycapBoxHistoryItemResponse(boxOpenId, "FREE", keycapId, 1, false, openedAt)),
                "next-cursor",
                true
        );
        when(keycapBoxQueryService.getHistory(authenticatedUserId(), "cursor-1", 10)).thenReturn(response);

        mockMvc.perform(get("/api/keycap-boxes/history")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer access-token")
                        .param("cursor", "cursor-1")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content[0].boxOpenId").value(boxOpenId.toString()))
                .andExpect(jsonPath("$.data.content[0].openMethod").value("FREE"))
                .andExpect(jsonPath("$.data.content[0].keycapId").value(keycapId.toString()))
                .andExpect(jsonPath("$.data.content[0].shardCount").value(1))
                .andExpect(jsonPath("$.data.content[0].completed").value(false))
                .andExpect(jsonPath("$.data.content[0].openedAt").value("2026-07-15T00:00:00Z"))
                .andExpect(jsonPath("$.data.nextCursor").value("next-cursor"))
                .andExpect(jsonPath("$.data.hasNext").value(true))
                .andExpect(jsonPath("$.data.content[0].id").doesNotExist())
                .andExpect(jsonPath("$.data.content[0].userId").doesNotExist())
                .andExpect(jsonPath("$.data.content[0].idempotencyKey").doesNotExist())
                .andExpect(jsonPath("$.data.content[0].requestHash").doesNotExist())
                .andExpect(jsonPath("$.data.content[0].adRewardId").doesNotExist())
                .andExpect(jsonPath("$.error").doesNotExist());
    }

    @Test
    void historyRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/keycap-boxes/history"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("AUTH_REQUIRED"));
    }

    private UUID authenticatedUserId() {
        return UUID.fromString("00000000-0000-0000-0000-000000000021");
    }

    private void stubAuthenticatedAccessToken(String token) {
        UUID userId = authenticatedUserId();
        UUID sessionId = UUID.randomUUID();
        JwtTokenProvider.JwtTokenClaims claims = new JwtTokenProvider.JwtTokenClaims(
                userId,
                sessionId,
                "access-jti",
                "access",
                Instant.now().getEpochSecond(),
                Instant.now().toEpochMilli(),
                Instant.now().plusSeconds(300)
        );
        AuthService.AuthSession session = new AuthService.AuthSession(
                sessionId,
                userId,
                "device-public-id",
                "refresh-jti-hash",
                "refresh-token-hash",
                "token-family-id-hash",
                null,
                null,
                Instant.now(),
                Instant.now().plusSeconds(3600),
                "ACTIVE"
        );

        when(authService.parseAccessToken(token)).thenReturn(claims);
        when(authService.isAccessDenied("access-jti")).thenReturn(false);
        when(authService.findUserRevokedAtMillis(userId)).thenReturn(Optional.empty());
        when(authService.findBySessionId(sessionId)).thenReturn(Optional.of(session));
    }
}
