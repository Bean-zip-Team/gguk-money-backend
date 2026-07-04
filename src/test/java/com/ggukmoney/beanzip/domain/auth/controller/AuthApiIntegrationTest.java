package com.ggukmoney.beanzip.domain.auth.controller;

import com.ggukmoney.beanzip.domain.auth.infra.RedisAuthSessionRepository;
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
    void refreshRotatesTokenAgainstRealRedisAndPersistsAuditLog() throws Exception {
        TestTokens tokens = saveTokenBackedSession(UUID.randomUUID().toString(), UUID.randomUUID().toString());

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + tokens.refreshToken() + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.traceId").exists())
                .andExpect(jsonPath("$.data.accessToken").exists())
                .andExpect(jsonPath("$.data.refreshToken").exists());

        assertThat(authSessionRepository.findBySessionId(tokens.session().sessionId()).orElseThrow().previousRefreshJtiHash())
                .isEqualTo(tokens.session().currentRefreshJtiHash());
        assertThat(jdbcTemplate.queryForObject("select count(*) from auth_session_log where event_type = 'REFRESHED' and result = 'SUCCESS'", Long.class))
                .isEqualTo(1L);
    }

    @Test
    void logoutDeletesCurrentSessionAgainstRealRedisAndPersistsAuditLog() throws Exception {
        TestTokens tokens = saveTokenBackedSession(UUID.randomUUID().toString(), UUID.randomUUID().toString());

        mockMvc.perform(post("/api/v1/auth/logout")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + tokens.refreshToken() + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.traceId").exists());

        assertThat(Boolean.TRUE.equals(redisTemplate.hasKey(RedisAuthSessionRepository.refreshKey(tokens.session().sessionId())))).isFalse();
        assertThat(jdbcTemplate.queryForObject("select count(*) from auth_session_log where event_type = 'LOGOUT' and result = 'SUCCESS'", Long.class))
                .isEqualTo(1L);
    }

    @Test
    void logoutAllDeletesAllSessionsAgainstRealRedisAndPersistsAuditLog() throws Exception {
        String userPublicId = UUID.randomUUID().toString();
        saveTokenBackedSession(userPublicId, UUID.randomUUID().toString());
        TestTokens current = saveTokenBackedSession(userPublicId, UUID.randomUUID().toString());

        mockMvc.perform(post("/api/v1/auth/logout-all")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + current.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.revokedSessionCount").value(2))
                .andExpect(jsonPath("$.traceId").exists());

        assertThat(Boolean.TRUE.equals(redisTemplate.hasKey(RedisAuthSessionRepository.userSessionsKey(userPublicId)))).isFalse();
        assertThat(jdbcTemplate.queryForObject("select count(*) from auth_session_log where event_type = 'LOGOUT_ALL' and result = 'SUCCESS'", Long.class))
                .isEqualTo(1L);
    }

    @Test
    void legacyAuthRefreshPathDoesNotSucceed() throws Exception {
        TestTokens tokens = saveTokenBackedSession(UUID.randomUUID().toString(), UUID.randomUUID().toString());

        mockMvc.perform(post(LEGACY_AUTH_REFRESH_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + tokens.refreshToken() + "\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("AUTH_REQUIRED"))
                .andExpect(jsonPath("$.traceId").exists());
    }

    @Test
    void logoutRejectsRefreshTokenFromDifferentSessionAndKeepsEverySession() throws Exception {
        String userPublicId = UUID.randomUUID().toString();
        TestTokens current = saveTokenBackedSession(userPublicId, UUID.randomUUID().toString());
        TestTokens other = saveTokenBackedSession(userPublicId, UUID.randomUUID().toString());

        mockMvc.perform(post("/api/v1/auth/logout")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + current.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + other.refreshToken() + "\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("AUTH_LOGOUT_SESSION_MISMATCH"))
                .andExpect(jsonPath("$.traceId").exists());

        assertThat(Boolean.TRUE.equals(redisTemplate.hasKey(RedisAuthSessionRepository.refreshKey(current.session().sessionId())))).isTrue();
        assertThat(Boolean.TRUE.equals(redisTemplate.hasKey(RedisAuthSessionRepository.refreshKey(other.session().sessionId())))).isTrue();
        assertThat(Boolean.TRUE.equals(redisTemplate.hasKey("auth:deny:access:" + current.accessJti()))).isFalse();
        assertThat(jdbcTemplate.queryForObject("select count(*) from auth_session_log where event_type = 'LOGOUT'", Long.class))
                .isZero();
    }

    @Test
    void logoutWithoutBodyDeletesOnlyCurrentSessionAndDeniesCurrentAccessJti() throws Exception {
        String userPublicId = UUID.randomUUID().toString();
        TestTokens current = saveTokenBackedSession(userPublicId, UUID.randomUUID().toString());
        TestTokens otherSameUser = saveTokenBackedSession(userPublicId, UUID.randomUUID().toString());
        TestTokens otherUser = saveTokenBackedSession(UUID.randomUUID().toString(), UUID.randomUUID().toString());

        mockMvc.perform(post("/api/v1/auth/logout")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + current.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.loggedOut").value(true))
                .andExpect(jsonPath("$.traceId").exists());

        assertThat(Boolean.TRUE.equals(redisTemplate.hasKey(RedisAuthSessionRepository.refreshKey(current.session().sessionId())))).isFalse();
        assertThat(Boolean.TRUE.equals(redisTemplate.hasKey(RedisAuthSessionRepository.refreshKey(otherSameUser.session().sessionId())))).isTrue();
        assertThat(Boolean.TRUE.equals(redisTemplate.hasKey(RedisAuthSessionRepository.refreshKey(otherUser.session().sessionId())))).isTrue();
        assertThat(redisTemplate.opsForValue().get("auth:deny:access:" + current.accessJti())).isEqualTo("1");
        assertThat(jdbcTemplate.queryForObject("select count(*) from auth_session_log where event_type = 'LOGOUT' and result = 'SUCCESS'", Long.class))
                .isEqualTo(1L);
    }

    @Test
    void refreshStateChangeSurvivesAuditFailureWhenUserPublicIdIsInvalidUuid() throws Exception {
        TestTokens tokens = saveTokenBackedSession("invalid-user-public-id", UUID.randomUUID().toString());

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + tokens.refreshToken() + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").exists())
                .andExpect(jsonPath("$.data.refreshToken").exists());

        assertThat(authSessionRepository.findBySessionId(tokens.session().sessionId()).orElseThrow().previousRefreshJtiHash())
                .isEqualTo(tokens.session().currentRefreshJtiHash());
        assertThat(jdbcTemplate.queryForObject("select count(*) from auth_session_log", Long.class)).isZero();
    }
}
