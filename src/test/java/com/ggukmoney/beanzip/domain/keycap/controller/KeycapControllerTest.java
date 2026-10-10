package com.ggukmoney.beanzip.domain.keycap.controller;

import com.ggukmoney.beanzip.domain.auth.service.AuthService;
import com.ggukmoney.beanzip.domain.auth.service.JwtTokenProvider;
import com.ggukmoney.beanzip.domain.keycap.dto.response.KeycapDrawResponse;
import com.ggukmoney.beanzip.domain.keycap.dto.response.KeycapEquipResponse;
import com.ggukmoney.beanzip.domain.keycap.dto.response.KeycapItemResponse;
import com.ggukmoney.beanzip.domain.keycap.dto.response.KeycapListResponse;
import com.ggukmoney.beanzip.domain.keycap.dto.response.MyKeycapItemResponse;
import com.ggukmoney.beanzip.domain.keycap.dto.response.MyKeycapListResponse;
import com.ggukmoney.beanzip.domain.keycap.service.KeycapDrawService;
import com.ggukmoney.beanzip.domain.keycap.service.KeycapService;
import com.ggukmoney.beanzip.global.common.GlobalExceptionHandler;
import com.ggukmoney.beanzip.global.interceptor.AuthInterceptor;
import com.ggukmoney.beanzip.global.interceptor.AuthRequestAttributes;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class KeycapControllerTest {

    private final AuthService authService = mock(AuthService.class);
    private final KeycapService keycapService = mock(KeycapService.class);
    private final KeycapDrawService keycapDrawService = mock(KeycapDrawService.class);
    private final KeycapController keycapController = new KeycapController(keycapService, keycapDrawService,
            mock(com.ggukmoney.beanzip.domain.keycap.service.KeycapPassiveService.class));
    private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(keycapController)
            .addInterceptors(new AuthInterceptor(authService))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

    @Test
    void getKeycapsCallsServiceAndWrapsResponse() {
        KeycapListResponse response = new KeycapListResponse(List.of());
        when(keycapService.getKeycaps()).thenReturn(response);

        var result = keycapController.getKeycaps();

        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().data()).isEqualTo(response);
        verify(keycapService).getKeycaps();
    }

    @Test
    void getMyKeycapsPassesAuthenticatedUserIdToService() {
        UUID userId = UUID.randomUUID();
        MyKeycapListResponse response = new MyKeycapListResponse(List.of());
        when(keycapService.getMyKeycaps(userId)).thenReturn(response);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(AuthRequestAttributes.USER_ID, userId);

        var result = keycapController.getMyKeycaps(request);

        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().data()).isEqualTo(response);
        verify(keycapService).getMyKeycaps(userId);
    }

    @Test
    void equipKeycapPassesAuthenticatedUserIdAndKeycapIdToService() {
        UUID userId = UUID.randomUUID();
        UUID keycapId = UUID.randomUUID();
        KeycapEquipResponse response = new KeycapEquipResponse(keycapId, true);
        when(keycapService.equipKeycap(userId, keycapId)).thenReturn(response);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(AuthRequestAttributes.USER_ID, userId);

        var result = keycapController.equipKeycap(request, keycapId);

        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().data()).isEqualTo(response);
        verify(keycapService).equipKeycap(userId, keycapId);
    }

    @Test
    void authenticatedGetKeycapsReturnsSuccessDataShape() throws Exception {
        stubAuthenticatedAccessToken("access-token");
        UUID keycapId = UUID.randomUUID();
        when(keycapService.getKeycaps()).thenReturn(new KeycapListResponse(List.of(
                new KeycapItemResponse(keycapId, "BASIC_001", "Basic", "COMMON", 10, 1, "BOX", null, null)
        )));

        mockMvc.perform(get("/api/keycaps")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer access-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.keycaps[0].keycapId").value(keycapId.toString()))
                .andExpect(jsonPath("$.data.keycaps[0].code").value("BASIC_001"))
                .andExpect(jsonPath("$.data.keycaps[0].grade").value("COMMON"))
                .andExpect(jsonPath("$.data.keycaps[0].requiredShardCount").value(10))
                .andExpect(jsonPath("$.data.keycaps[0].season").value(1))
                .andExpect(jsonPath("$.data.keycaps[0].acquisitionType").value("BOX"))
                .andExpect(jsonPath("$.data.keycaps[0].id").doesNotExist())
                .andExpect(jsonPath("$.error").doesNotExist());
    }

    @Test
    void authenticatedGetMyKeycapsReturnsSuccessDataShape() throws Exception {
        stubAuthenticatedAccessToken("access-token");
        UUID keycapId = UUID.randomUUID();
        when(keycapService.getMyKeycaps(authenticatedUserId())).thenReturn(new MyKeycapListResponse(List.of(
                new MyKeycapItemResponse(keycapId, "BASIC_001", "Basic", 10, "COMPLETED", true, "COMMON", "BOX", "https://example.com/keycap.png", null)
        )));

        mockMvc.perform(get("/api/keycaps/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer access-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.keycaps[0].keycapId").value(keycapId.toString()))
                .andExpect(jsonPath("$.data.keycaps[0].code").value("BASIC_001"))
                .andExpect(jsonPath("$.data.keycaps[0].level").value(10))
                .andExpect(jsonPath("$.data.keycaps[0].shardCount").doesNotExist())
                .andExpect(jsonPath("$.data.keycaps[0].grade").value("COMMON"))
                .andExpect(jsonPath("$.data.keycaps[0].acquisitionType").value("BOX"))
                .andExpect(jsonPath("$.data.keycaps[0].imageUrl").value("https://example.com/keycap.png"))
                .andExpect(jsonPath("$.data.keycaps[0].status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.keycaps[0].equipped").value(true))
                .andExpect(jsonPath("$.data.keycaps[0].id").doesNotExist())
                .andExpect(jsonPath("$.error").doesNotExist());
    }

    @Test
    void authenticatedEquipKeycapReturnsSuccessDataShape() throws Exception {
        stubAuthenticatedAccessToken("access-token");
        UUID keycapId = UUID.randomUUID();
        when(keycapService.equipKeycap(authenticatedUserId(), keycapId))
                .thenReturn(new KeycapEquipResponse(keycapId, true));

        mockMvc.perform(put("/api/keycaps/{keycapId}/equip", keycapId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer access-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.keycapId").value(keycapId.toString()))
                .andExpect(jsonPath("$.data.equipped").value(true))
                .andExpect(jsonPath("$.data.id").doesNotExist())
                .andExpect(jsonPath("$.error").doesNotExist());
    }

    @Test
    void unauthenticatedGetKeycapsIsRejectedByExistingAuthPolicy() throws Exception {
        mockMvc.perform(get("/api/keycaps"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("AUTH_REQUIRED"));
    }

    @Test
    void unauthenticatedGetMyKeycapsIsRejectedByExistingAuthPolicy() throws Exception {
        mockMvc.perform(get("/api/keycaps/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("AUTH_REQUIRED"));
    }

    @Test
    void unauthenticatedEquipKeycapIsRejectedByExistingAuthPolicy() throws Exception {
        mockMvc.perform(put("/api/keycaps/{keycapId}/equip", UUID.randomUUID()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("AUTH_REQUIRED"));
    }

    @Test
    void authenticatedDrawReturnsDrawResponse() throws Exception {
        stubAuthenticatedAccessToken("access-token");
        UUID drawId = UUID.randomUUID();
        UUID keycapId = UUID.randomUUID();
        Instant drawnAt = Instant.parse("2026-10-09T01:00:00Z");
        when(keycapDrawService.draw(authenticatedUserId(), "idem-key")).thenReturn(new KeycapDrawResponse(
                drawId, keycapId, "RARE_001", "Rare", "RARE", "https://example.com/rare.png", null, false, 9, 5, 12, drawnAt));

        mockMvc.perform(post("/api/keycaps/draw")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer access-token")
                        .header("Idempotency-Key", "idem-key"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.drawId").value(drawId.toString()))
                .andExpect(jsonPath("$.data.keycapId").value(keycapId.toString()))
                .andExpect(jsonPath("$.data.code").value("RARE_001"))
                .andExpect(jsonPath("$.data.grade").value("RARE"))
                .andExpect(jsonPath("$.data.newlyAcquired").value(false))
                .andExpect(jsonPath("$.data.level").value(9))
                .andExpect(jsonPath("$.data.shardsSpent").value(5))
                .andExpect(jsonPath("$.data.shardBalance").value(12))
                .andExpect(jsonPath("$.data.drawnAt").value("2026-10-09T01:00:00Z"))
                .andExpect(jsonPath("$.data.id").doesNotExist())
                .andExpect(jsonPath("$.error").doesNotExist());
    }

    @Test
    void drawRequiresAuthentication() throws Exception {
        mockMvc.perform(post("/api/keycaps/draw").header("Idempotency-Key", "idem-key"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("AUTH_REQUIRED"));
    }

    @Test
    void drawWithoutIdempotencyKeyIsBadRequest() throws Exception {
        stubAuthenticatedAccessToken("access-token");
        when(keycapDrawService.draw(eq(authenticatedUserId()), isNull()))
                .thenThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "IDEMPOTENCY_KEY_REQUIRED"));

        mockMvc.perform(post("/api/keycaps/draw")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer access-token"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("IDEMPOTENCY_KEY_REQUIRED"));
    }

    @Test
    void drawWithInsufficientShardsIsBadRequest() throws Exception {
        stubAuthenticatedAccessToken("access-token");
        when(keycapDrawService.draw(authenticatedUserId(), "idem-key"))
                .thenThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "KEYCAP_SHARD_INSUFFICIENT"));

        mockMvc.perform(post("/api/keycaps/draw")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer access-token")
                        .header("Idempotency-Key", "idem-key"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("KEYCAP_SHARD_INSUFFICIENT"));
    }

    private UUID authenticatedUserId() {
        return UUID.fromString("00000000-0000-0000-0000-000000000015");
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
