package com.ggukmoney.beanzip.global.config.controller;

import com.ggukmoney.beanzip.domain.auth.service.AuthService;
import com.ggukmoney.beanzip.domain.auth.service.JwtTokenProvider;
import com.ggukmoney.beanzip.global.common.GlobalExceptionHandler;
import com.ggukmoney.beanzip.global.config.dto.response.AppConfigResponse;
import com.ggukmoney.beanzip.global.config.service.AppConfigService;
import com.ggukmoney.beanzip.global.interceptor.AuthInterceptor;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AppConfigControllerTest {

    private final AuthService authService = mock(AuthService.class);
    private final AppConfigService appConfigService = mock(AppConfigService.class);
    private final AppConfigController appConfigController = new AppConfigController(appConfigService);
    private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(appConfigController)
            .addInterceptors(new AuthInterceptor(authService))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

    @Test
    void authenticatedRequestReturnsPublicAppConfig() throws Exception {
        stubAuthenticatedAccessToken("access-token");
        when(appConfigService.getAppConfig()).thenReturn(new AppConfigResponse(
                new AppConfigResponse.PointPolicy(20),
                new AppConfigResponse.BoxPolicy(200),
                new AppConfigResponse.BoosterPolicy(300, 3)
        ));

        mockMvc.perform(get("/api/v1/app-config")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer access-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.pointPolicy.dailyLimit").value(20))
                .andExpect(jsonPath("$.data.boxPolicy.baseRequiredTapCount").value(200))
                .andExpect(jsonPath("$.data.boosterPolicy.durationSeconds").value(300))
                .andExpect(jsonPath("$.data.boosterPolicy.dailyLimit").value(3))
                .andExpect(jsonPath("$.data.configKey").doesNotExist())
                .andExpect(jsonPath("$.data.configValue").doesNotExist())
                .andExpect(jsonPath("$.data.publicId").doesNotExist())
                .andExpect(jsonPath("$.data.effectiveAt").doesNotExist())
                .andExpect(jsonPath("$.data.boxPolicy.nextBoxRequiredTapCount").doesNotExist())
                .andExpect(jsonPath("$.data.boosterPolicy.multiplier").doesNotExist())
                .andExpect(jsonPath("$.error").doesNotExist());
    }

    @Test
    void unauthenticatedRequestIsRejectedByExistingAuthPolicy() throws Exception {
        mockMvc.perform(get("/api/v1/app-config"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("AUTH_REQUIRED"));
    }

    private void stubAuthenticatedAccessToken(String token) {
        UUID userId = UUID.randomUUID();
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
