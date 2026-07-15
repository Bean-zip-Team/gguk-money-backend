package com.ggukmoney.beanzip.domain.auth.controller;

import com.ggukmoney.beanzip.domain.auth.service.AuthService;
import com.ggukmoney.beanzip.support.FullStackIntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

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
        assertThat(Boolean.TRUE.equals(redisTemplate.hasKey("auth:deny:access:" + current.accessJti()))).isFalse();
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
        assertThat(redisTemplate.opsForValue().get("auth:deny:access:" + current.accessJti())).isEqualTo("1");
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
