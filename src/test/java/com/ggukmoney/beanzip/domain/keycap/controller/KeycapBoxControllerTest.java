package com.ggukmoney.beanzip.domain.keycap.controller;

import com.ggukmoney.beanzip.domain.auth.service.AuthService;
import com.ggukmoney.beanzip.domain.auth.service.JwtTokenProvider;
import com.ggukmoney.beanzip.domain.keycap.dto.response.KeycapBoxStatusResponse;
import com.ggukmoney.beanzip.domain.keycap.service.KeycapBoxStatusService;
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
    private final KeycapBoxStatusService keycapBoxStatusService = mock(KeycapBoxStatusService.class);
    private final KeycapBoxController keycapBoxController = new KeycapBoxController(keycapBoxStatusService);
    private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(keycapBoxController)
            .addInterceptors(new AuthInterceptor(authService))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

    @Test
    void getStatusPassesAuthenticatedUserIdToService() {
        UUID userId = UUID.randomUUID();
        KeycapBoxStatusResponse response = new KeycapBoxStatusResponse(2, 1, 45, 100);
        when(keycapBoxStatusService.getStatus(userId)).thenReturn(response);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(AuthRequestAttributes.USER_ID, userId);

        var result = keycapBoxController.getStatus(request);

        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().data()).isEqualTo(response);
        verify(keycapBoxStatusService).getStatus(userId);
    }

    @Test
    void authenticatedGetStatusReturnsFinalFourFields() throws Exception {
        stubAuthenticatedAccessToken("access-token");
        when(keycapBoxStatusService.getStatus(authenticatedUserId()))
                .thenReturn(new KeycapBoxStatusResponse(2, 1, 45, 100));

        mockMvc.perform(get("/api/v1/keycap-boxes/status")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer access-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.boxBalance").value(2))
                .andExpect(jsonPath("$.data.freeOpenTicketCount").value(1))
                .andExpect(jsonPath("$.data.boxProgressTapCount").value(45))
                .andExpect(jsonPath("$.data.nextBoxRequiredTapCount").value(100))
                .andExpect(jsonPath("$.data.id").doesNotExist())
                .andExpect(jsonPath("$.data.publicId").doesNotExist())
                .andExpect(jsonPath("$.data.nextFreeTicketAt").doesNotExist())
                .andExpect(jsonPath("$.data.adOpenCount").doesNotExist())
                .andExpect(jsonPath("$.error").doesNotExist());
    }

    @Test
    void unauthenticatedGetStatusIsRejectedByExistingAuthPolicy() throws Exception {
        mockMvc.perform(get("/api/v1/keycap-boxes/status"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("AUTH_REQUIRED"));
    }

    @Test
    void missingTapProgressUsesExistingTapProgressErrorCode() throws Exception {
        stubAuthenticatedAccessToken("access-token");
        when(keycapBoxStatusService.getStatus(authenticatedUserId()))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "TAP_PROGRESS_NOT_FOUND"));

        mockMvc.perform(get("/api/v1/keycap-boxes/status")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer access-token"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("TAP_PROGRESS_NOT_FOUND"));
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
