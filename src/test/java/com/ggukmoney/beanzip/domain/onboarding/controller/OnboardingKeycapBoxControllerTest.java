package com.ggukmoney.beanzip.domain.onboarding.controller;

import com.ggukmoney.beanzip.domain.auth.service.AuthService;
import com.ggukmoney.beanzip.domain.onboarding.dto.response.OnboardingKeycapBoxOpenResponse;
import com.ggukmoney.beanzip.domain.onboarding.service.OnboardingKeycapBoxOpenService;
import com.ggukmoney.beanzip.global.common.GlobalExceptionHandler;
import com.ggukmoney.beanzip.global.interceptor.AuthInterceptor;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class OnboardingKeycapBoxControllerTest {

    private final AuthService authService = mock(AuthService.class);
    private final OnboardingKeycapBoxOpenService service = mock(OnboardingKeycapBoxOpenService.class);
    private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new OnboardingKeycapBoxController(service))
            .addInterceptors(new AuthInterceptor(authService))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

    @Test
    void opensOnboardingBoxWithoutAuthentication() throws Exception {
        UUID attemptId = UUID.randomUUID();
        UUID keycapId = UUID.randomUUID();
        when(service.open(any())).thenReturn(new OnboardingKeycapBoxOpenResponse(
                attemptId,
                keycapId,
                "ONBOARDING_BASIC",
                "온보딩 키캡",
                "COMMON",
                "https://example.com/keycaps/onboarding-basic.png",
                "https://example.com/keycaps/onboarding-basic.mp3",
                true,
                100,
                Instant.parse("2026-07-15T01:00:05Z"),
                Instant.parse("2026-07-16T01:00:05Z")
        ));

        mockMvc.perform(post("/api/onboarding/keycap-boxes/open")
                        .contentType("application/json")
                        .content(validBody()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.onboardingAttemptId").value(attemptId.toString()))
                .andExpect(jsonPath("$.data.keycapId").value(keycapId.toString()))
                .andExpect(jsonPath("$.data.code").value("ONBOARDING_BASIC"))
                .andExpect(jsonPath("$.data.name").value("온보딩 키캡"))
                .andExpect(jsonPath("$.data.grade").value("COMMON"))
                .andExpect(jsonPath("$.data.imageUrl").value("https://example.com/keycaps/onboarding-basic.png"))
                .andExpect(jsonPath("$.data.soundUrl").value("https://example.com/keycaps/onboarding-basic.mp3"))
                .andExpect(jsonPath("$.data.completed").value(true))
                .andExpect(jsonPath("$.data.rewardPoint").value(100))
                .andExpect(jsonPath("$.data.openedAt").value("2026-07-15T01:00:05Z"))
                .andExpect(jsonPath("$.data.expiresAt").value("2026-07-16T01:00:05Z"))
                .andExpect(jsonPath("$.data.id").doesNotExist())
                .andExpect(jsonPath("$.data.tapSessionId").doesNotExist())
                .andExpect(jsonPath("$.data.requestHash").doesNotExist())
                .andExpect(jsonPath("$.error").doesNotExist());

        verifyNoInteractions(authService);
        verify(service).open(any());
    }

    @Test
    void protectedKeycapBoxStatusStillRequiresAuthentication() {
        AuthInterceptor interceptor = new AuthInterceptor(authService);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/keycap-boxes/status");

        assertThatThrownBy(() -> interceptor.preHandle(request, new MockHttpServletResponse(), new Object()))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(exception -> ((ResponseStatusException) exception).getReason())
                .isEqualTo("AUTH_REQUIRED");
    }

    @Test
    void invalidRequestBodyUsesCommonValidationError() throws Exception {
        mockMvc.perform(post("/api/onboarding/keycap-boxes/open")
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("COMMON_VALIDATION_ERROR"));
    }

    @Test
    void serviceErrorUsesOnboardingErrorCode() throws Exception {
        when(service.open(any())).thenThrow(new ResponseStatusException(CONFLICT, "ONBOARDING_TAP_SESSION_REUSED"));

        mockMvc.perform(post("/api/onboarding/keycap-boxes/open")
                        .contentType("application/json")
                        .content(validBody()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("ONBOARDING_TAP_SESSION_REUSED"));
    }

    private static String validBody() {
        String events = IntStream.rangeClosed(1, 45)
                .mapToObj(sequence -> """
                        {
                          "sequence": %d,
                          "occurredAt": "2026-07-15T01:00:%02dZ"
                        }
                        """.formatted(sequence, sequence % 60))
                .collect(Collectors.joining(","));
        return """
                {
                  "tapSessionId": "57ba7793-8a9b-4c65-8d91-94dc47ce0642",
                  "tapEvents": [%s]
                }
                """.formatted(events);
    }
}
