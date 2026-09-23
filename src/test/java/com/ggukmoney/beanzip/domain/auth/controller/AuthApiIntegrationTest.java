package com.ggukmoney.beanzip.domain.auth.controller;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.ggukmoney.beanzip.domain.auth.service.AuthService;
import com.ggukmoney.beanzip.global.interceptor.AuthRequestAttributes;
import com.ggukmoney.beanzip.support.FullStackIntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthApiIntegrationTest extends FullStackIntegrationTestSupport {

    private static final String LEGACY_AUTH_REFRESH_PATH = "/auth" + "/refresh";

    @Test
    void refreshRotatesTokenAgainstRealRedis() throws Exception {
        TestTokens tokens = saveTokenBackedSession(UUID.randomUUID(), UUID.randomUUID().toString());

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + tokens.refreshToken() + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$." + "trace" + "Id").doesNotExist())
                .andExpect(jsonPath("$.requestId").doesNotExist())
                .andExpect(jsonPath("$.data.accessToken").exists())
                .andExpect(jsonPath("$.data.refreshToken").exists());

        assertThat(authService.findBySessionId(tokens.session().sessionId()).orElseThrow().previousRefreshJtiHash())
                .isEqualTo(tokens.session().currentRefreshJtiHash());
    }

    @Test
    void refreshRecordsPrincipalSoAccessLogShowsWhoRefreshed() throws Exception {
        UUID userId = UUID.randomUUID();
        TestTokens tokens = saveTokenBackedSession(userId, UUID.randomUUID().toString());

        MvcResult result = mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + tokens.refreshToken() + "\"}"))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(result.getRequest().getAttribute(AuthRequestAttributes.USER_ID)).isEqualTo(userId);
        assertThat(result.getRequest().getAttribute(AuthRequestAttributes.SESSION_ID))
                .isEqualTo(tokens.session().sessionId());
    }

    @Test
    void failedRefreshAlsoRecordsPrincipalSoLogoutCauseIsTraceable() throws Exception {
        UUID userId = UUID.randomUUID();
        TestTokens tokens = saveTokenBackedSession(userId, UUID.randomUUID().toString());
        authService.deleteSession(tokens.session().sessionId());

        MvcResult result = mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + tokens.refreshToken() + "\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_SESSION_NOT_FOUND"))
                .andReturn();

        assertThat(result.getRequest().getAttribute(AuthRequestAttributes.USER_ID)).isEqualTo(userId);
        assertThat(result.getRequest().getAttribute(AuthRequestAttributes.SESSION_ID))
                .isEqualTo(tokens.session().sessionId());
    }

    @Test
    void refreshLogsOutcomeWithTokenGenerationOfThePresentedToken() throws Exception {
        UUID userId = UUID.randomUUID();
        TestTokens tokens = saveTokenBackedSession(userId, UUID.randomUUID().toString());
        Logger logger = (Logger) LoggerFactory.getLogger(AuthService.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);

        try {
            mockMvc.perform(post("/api/auth/refresh")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"refreshToken\":\"" + tokens.refreshToken() + "\"}"))
                    .andExpect(status().isOk());

            // 교체된 뒤 같은 토큰을 다시 보내면 직전 세대 토큰으로 판정된다. 앱이 새 토큰을 못 받았을 때 나타나는 형태다.
            mockMvc.perform(post("/api/auth/refresh")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"refreshToken\":\"" + tokens.refreshToken() + "\"}"))
                    .andExpect(status().isConflict());

            assertThat(appender.list)
                    .extracting(ILoggingEvent::getFormattedMessage)
                    .anySatisfy(message -> assertThat(message)
                            .contains("AUTH_REFRESH", "result=ROTATED", "presented=CURRENT", "userId=" + userId))
                    .anySatisfy(message -> assertThat(message)
                            .contains("AUTH_REFRESH", "result=CONFLICT", "presented=PREVIOUS", "userId=" + userId));
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    @Test
    void logoutDeletesCurrentSessionAgainstRealRedis() throws Exception {
        TestTokens tokens = saveTokenBackedSession(UUID.randomUUID(), UUID.randomUUID().toString());

        mockMvc.perform(post("/api/auth/logout")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"" + tokens.refreshToken() + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$." + "trace" + "Id").doesNotExist())
                .andExpect(jsonPath("$.requestId").doesNotExist());

        assertThat(Boolean.TRUE.equals(redisTemplate.hasKey(AuthService.refreshKey(tokens.session().sessionId())))).isFalse();
    }

    @Test
    void logoutAllDeletesAllSessionsAgainstRealRedis() throws Exception {
        UUID userId = UUID.randomUUID();
        saveTokenBackedSession(userId, UUID.randomUUID().toString());
        TestTokens current = saveTokenBackedSession(userId, UUID.randomUUID().toString());

        mockMvc.perform(post("/api/auth/logout-all")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + current.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.revokedSessionCount").value(2))
                .andExpect(jsonPath("$." + "trace" + "Id").doesNotExist())
                .andExpect(jsonPath("$.requestId").doesNotExist());

        assertThat(Boolean.TRUE.equals(redisTemplate.hasKey(AuthService.userSessionsKey(userId)))).isFalse();
    }

    @Test
    void legacyAuthRefreshPathDoesNotSucceed() throws Exception {
        TestTokens tokens = saveTokenBackedSession(UUID.randomUUID(), UUID.randomUUID().toString());

        mockMvc.perform(post(LEGACY_AUTH_REFRESH_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + tokens.refreshToken() + "\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("AUTH_REQUIRED"))
                .andExpect(jsonPath("$." + "trace" + "Id").doesNotExist())
                .andExpect(jsonPath("$.requestId").doesNotExist());
    }

    @Test
    void logoutRejectsRefreshTokenFromDifferentSessionAndKeepsEverySession() throws Exception {
        UUID userId = UUID.randomUUID();
        TestTokens current = saveTokenBackedSession(userId, UUID.randomUUID().toString());
        TestTokens other = saveTokenBackedSession(userId, UUID.randomUUID().toString());

        mockMvc.perform(post("/api/auth/logout")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + current.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + other.refreshToken() + "\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("AUTH_LOGOUT_SESSION_MISMATCH"))
                .andExpect(jsonPath("$." + "trace" + "Id").doesNotExist())
                .andExpect(jsonPath("$.requestId").doesNotExist());

        assertThat(Boolean.TRUE.equals(redisTemplate.hasKey(AuthService.refreshKey(current.session().sessionId())))).isTrue();
        assertThat(Boolean.TRUE.equals(redisTemplate.hasKey(AuthService.refreshKey(other.session().sessionId())))).isTrue();
        assertThat(Boolean.TRUE.equals(redisTemplate.hasKey("ggukmoney:auth:deny:access:" + current.accessJti()))).isFalse();
    }

    @Test
    void logoutWithoutBodyDeletesOnlyCurrentSessionAndDeniesCurrentAccessJti() throws Exception {
        UUID userId = UUID.randomUUID();
        TestTokens current = saveTokenBackedSession(userId, UUID.randomUUID().toString());
        TestTokens otherSameUser = saveTokenBackedSession(userId, UUID.randomUUID().toString());
        TestTokens otherUser = saveTokenBackedSession(UUID.randomUUID(), UUID.randomUUID().toString());

        mockMvc.perform(post("/api/auth/logout")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + current.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.loggedOut").value(true))
                .andExpect(jsonPath("$." + "trace" + "Id").doesNotExist())
                .andExpect(jsonPath("$.requestId").doesNotExist());

        assertThat(Boolean.TRUE.equals(redisTemplate.hasKey(AuthService.refreshKey(current.session().sessionId())))).isFalse();
        assertThat(Boolean.TRUE.equals(redisTemplate.hasKey(AuthService.refreshKey(otherSameUser.session().sessionId())))).isTrue();
        assertThat(Boolean.TRUE.equals(redisTemplate.hasKey(AuthService.refreshKey(otherUser.session().sessionId())))).isTrue();
        assertThat(redisTemplate.opsForValue().get("ggukmoney:auth:deny:access:" + current.accessJti())).isEqualTo("1");
    }

    @Test
    void refreshStateChangeUsesUuidUserId() throws Exception {
        TestTokens tokens = saveTokenBackedSession(UUID.randomUUID(), UUID.randomUUID().toString());

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + tokens.refreshToken() + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").exists())
                .andExpect(jsonPath("$.data.refreshToken").exists());

        assertThat(authService.findBySessionId(tokens.session().sessionId()).orElseThrow().previousRefreshJtiHash())
                .isEqualTo(tokens.session().currentRefreshJtiHash());
    }
}
