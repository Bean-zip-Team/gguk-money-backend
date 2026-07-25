package com.ggukmoney.beanzip.domain.keycap.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ggukmoney.beanzip.domain.auth.service.AuthService;
import com.ggukmoney.beanzip.domain.auth.service.JwtTokenProvider;
import com.ggukmoney.beanzip.domain.keycap.dto.request.KeycapBoxOpenRequest;
import com.ggukmoney.beanzip.domain.keycap.dto.response.KeycapBoxHistoryItemResponse;
import com.ggukmoney.beanzip.domain.keycap.dto.response.KeycapBoxHistoryResponse;
import com.ggukmoney.beanzip.domain.keycap.dto.response.KeycapBoxOpenResponse;
import com.ggukmoney.beanzip.domain.keycap.dto.response.KeycapBoxStatusResponse;
import com.ggukmoney.beanzip.domain.keycap.service.KeycapBoxOpenService;
import com.ggukmoney.beanzip.domain.keycap.service.KeycapBoxQueryService;
import com.ggukmoney.beanzip.global.common.GlobalExceptionHandler;
import com.ggukmoney.beanzip.global.interceptor.AuthInterceptor;
import com.ggukmoney.beanzip.global.interceptor.AuthRequestAttributes;
import io.swagger.v3.oas.annotations.Parameter;
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

import java.time.Instant;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class KeycapBoxControllerTest {

    private final AuthService authService = mock(AuthService.class);
    private final KeycapBoxQueryService keycapBoxQueryService = mock(KeycapBoxQueryService.class);
    private final KeycapBoxOpenService keycapBoxOpenService = mock(KeycapBoxOpenService.class);
    private final KeycapBoxController keycapBoxController =
            new KeycapBoxController(keycapBoxQueryService, keycapBoxOpenService);
    private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(keycapBoxController)
            .addInterceptors(new AuthInterceptor(authService))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

    @Test
    void getStatusPassesAuthenticatedUserIdToService() {
        UUID userId = UUID.randomUUID();
        KeycapBoxStatusResponse response = new KeycapBoxStatusResponse(2, true, true, false, null, 45, 100);
        when(keycapBoxQueryService.getStatus(userId)).thenReturn(response);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(AuthRequestAttributes.USER_ID, userId);

        var result = keycapBoxController.getStatus(request);

        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().data()).isEqualTo(response);
        verify(keycapBoxQueryService).getStatus(userId);
    }

    @Test
    void authenticatedGetStatusReturnsCycleStateFields() throws Exception {
        stubAuthenticatedAccessToken("access-token");
        when(keycapBoxQueryService.getStatus(authenticatedUserId()))
                .thenReturn(new KeycapBoxStatusResponse(2, true, false, false, null, 45, 100));

        var result = mockMvc.perform(get("/api/keycap-boxes/status")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer access-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.boxBalance").value(2))
                .andExpect(jsonPath("$.data.canFreeOpen").value(true))
                .andExpect(jsonPath("$.data.canAdOpen").value(false))
                .andExpect(jsonPath("$.data.charging").value(false))
                .andExpect(jsonPath("$.data.nextRechargeAt").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.data.boxProgressTapCount").value(45))
                .andExpect(jsonPath("$.data.nextBoxRequiredTapCount").value(100))
                .andExpect(jsonPath("$.data.id").doesNotExist())
                .andExpect(jsonPath("$.data.publicId").doesNotExist())
                .andExpect(jsonPath("$.data.freeOpenTicketCount").doesNotExist())
                .andExpect(jsonPath("$.data.nextFreeTicketAt").doesNotExist())
                .andExpect(jsonPath("$.data.adOpenCount").doesNotExist())
                .andExpect(jsonPath("$.error").doesNotExist())
                .andReturn();

        JsonNode data = new ObjectMapper().readTree(result.getResponse().getContentAsString()).path("data");
        assertThat(data.has("nextRechargeAt")).isTrue();
        assertThat(data.get("nextRechargeAt").isNull()).isTrue();
    }

    @Test
    void authenticatedGetStatusReturnsRechargeTimeWhenChargingWithoutBox() throws Exception {
        stubAuthenticatedAccessToken("access-token");
        when(keycapBoxQueryService.getStatus(authenticatedUserId())).thenReturn(
                new KeycapBoxStatusResponse(0, false, false, true, Instant.parse("2026-07-16T01:00:00Z"), 45, 100)
        );

        mockMvc.perform(get("/api/keycap-boxes/status")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer access-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.boxBalance").value(0))
                .andExpect(jsonPath("$.data.canFreeOpen").value(false))
                .andExpect(jsonPath("$.data.canAdOpen").value(false))
                .andExpect(jsonPath("$.data.charging").value(true))
                .andExpect(jsonPath("$.data.nextRechargeAt").value("2026-07-16T01:00:00Z"));
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
    void authenticatedOpenReturnsBoxOpenResponse() throws Exception {
        stubAuthenticatedAccessToken("access-token");
        UUID boxOpenId = UUID.randomUUID();
        UUID keycapId = UUID.randomUUID();
        Instant openedAt = Instant.parse("2026-07-14T00:00:00Z");
        when(keycapBoxOpenService.open(eq(authenticatedUserId()), eq("idem-key"), any()))
                .thenReturn(new KeycapBoxOpenResponse(boxOpenId, keycapId, "https://example.com/keycaps/cheer.webp", 1, false, openedAt));

        mockMvc.perform(post("/api/keycap-boxes/open")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer access-token")
                        .header("Idempotency-Key", "idem-key")
                        .contentType("application/json")
                        .content("""
                                {
                                  "openMethod": "FREE"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.boxOpenId").value(boxOpenId.toString()))
                .andExpect(jsonPath("$.data.keycapId").value(keycapId.toString()))
                .andExpect(jsonPath("$.data.imageUrl").value("https://example.com/keycaps/cheer.webp"))
                .andExpect(jsonPath("$.data.shardCount").value(1))
                .andExpect(jsonPath("$.data.completed").value(false))
                .andExpect(jsonPath("$.data.openedAt").value("2026-07-14T00:00:00Z"))
                .andExpect(jsonPath("$.data.id").doesNotExist())
                .andExpect(jsonPath("$.data.boostApplied").doesNotExist())
                .andExpect(jsonPath("$.error").doesNotExist());
    }

    @Test
    void openRequiresAuthentication() throws Exception {
        mockMvc.perform(post("/api/keycap-boxes/open")
                        .header("Idempotency-Key", "idem-key")
                        .contentType("application/json")
                        .content("""
                                {
                                  "openMethod": "FREE"
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("AUTH_REQUIRED"));
    }

    @Test
    void openRequiresIdempotencyKey() throws Exception {
        stubAuthenticatedAccessToken("access-token");
        when(keycapBoxOpenService.open(eq(authenticatedUserId()), isNull(), any()))
                .thenThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "IDEMPOTENCY_KEY_REQUIRED"));

        mockMvc.perform(post("/api/keycap-boxes/open")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer access-token")
                        .contentType("application/json")
                        .content("""
                                {
                                  "openMethod": "FREE"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("IDEMPOTENCY_KEY_REQUIRED"));
    }

    @Test
    void openRejectsInvalidRequestBody() throws Exception {
        stubAuthenticatedAccessToken("access-token");

        mockMvc.perform(post("/api/keycap-boxes/open")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer access-token")
                        .header("Idempotency-Key", "idem-key")
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("COMMON_VALIDATION_ERROR"));
    }

    @Test
    void openAdvertisementDailyLimitExceededResponse() throws Exception {
        stubAuthenticatedAccessToken("access-token");
        when(keycapBoxOpenService.open(eq(authenticatedUserId()), eq("idem-key"), any()))
                .thenThrow(new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "AD_OPEN_LIMIT_EXCEEDED"));

        mockMvc.perform(post("/api/keycap-boxes/open")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer access-token")
                        .header("Idempotency-Key", "idem-key")
                        .contentType("application/json")
                        .content("""
                                {
                                  "openMethod": "ADVERTISEMENT",
                                  "adRewardId": "ad-1"
                                }
                                """))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("AD_OPEN_LIMIT_EXCEEDED"));
    }

    @Test
    void openSwaggerDocumentsIdempotencyKeyLengthAndRateLimitResponse() throws Exception {
        Method method = KeycapBoxController.class.getDeclaredMethod(
                "open",
                String.class,
                KeycapBoxOpenRequest.class,
                jakarta.servlet.http.HttpServletRequest.class
        );

        Parameter parameter = method.getParameters()[0].getAnnotation(Parameter.class);
        ApiResponses responses = method.getAnnotation(ApiResponses.class);

        assertThat(parameter.description()).contains("최대 100자");
        assertThat(parameter.description()).doesNotContain("128");
        assertThat(responses.value())
                .extracting(io.swagger.v3.oas.annotations.responses.ApiResponse::responseCode)
                .contains("429");
    }

    @Test
    void statusSwaggerDocumentsBoxIndependentChargingExamples() throws Exception {
        Method method = KeycapBoxController.class.getDeclaredMethod(
                "getStatus",
                jakarta.servlet.http.HttpServletRequest.class
        );
        ApiResponses responses = method.getAnnotation(ApiResponses.class);
        ApiResponse success = java.util.Arrays.stream(responses.value())
                .filter(response -> response.responseCode().equals("200"))
                .findFirst()
                .orElseThrow();
        Content content = success.content()[0];
        Schema canFreeOpen = KeycapBoxStatusResponse.class.getRecordComponents()[1]
                .getAccessor()
                .getAnnotation(Schema.class);
        Schema canAdOpen = KeycapBoxStatusResponse.class.getRecordComponents()[2]
                .getAccessor()
                .getAnnotation(Schema.class);
        Schema charging = KeycapBoxStatusResponse.class.getRecordComponents()[3]
                .getAccessor()
                .getAnnotation(Schema.class);
        Schema nextRechargeAt = KeycapBoxStatusResponse.class.getRecordComponents()[4]
                .getAccessor()
                .getAnnotation(Schema.class);

        assertThat(content.examples()).extracting(io.swagger.v3.oas.annotations.media.ExampleObject::name)
                .containsExactly("상자 없음 - 개봉 횟수 남음", "상자 없음 - 공통 주기 충전 중");
        assertThat(canFreeOpen.description()).contains("상자").contains("무료");
        assertThat(canAdOpen.description()).contains("상자").contains("광고");
        assertThat(charging.description()).contains("상자 보유 여부와 무관");
        assertThat(nextRechargeAt.description()).contains("charging=true").contains("null");
    }

    @Test
    void openRequestSwaggerDocumentsAdRewardIdAsOptionalProviderVerificationField() throws Exception {
        Schema adRewardIdSchema = KeycapBoxOpenRequest.class
                .getRecordComponents()[1]
                .getAccessor()
                .getAnnotation(Schema.class);

        assertThat(adRewardIdSchema.description()).contains("선택");
        assertThat(adRewardIdSchema.description()).contains("광고 Provider 검증");
        assertThat(adRewardIdSchema.description()).doesNotContain("필수");
        assertThat(adRewardIdSchema.example()).isBlank();
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
                .andExpect(jsonPath("$.data.content[0].boostApplied").doesNotExist())
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
