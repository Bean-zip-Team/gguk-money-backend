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

    @Test
    void refreshRotatesTokenAgainstRealRedisAndPersistsAuditLog() throws Exception {
        TestTokens tokens = saveTokenBackedSession(UUID.randomUUID().toString(), UUID.randomUUID().toString());

        mockMvc.perform(post("/auth/refresh")
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

        mockMvc.perform(post("/auth/logout")
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

        mockMvc.perform(post("/auth/logout-all")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + current.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.revokedSessionCount").value(2))
                .andExpect(jsonPath("$.traceId").exists());

        assertThat(Boolean.TRUE.equals(redisTemplate.hasKey(RedisAuthSessionRepository.userSessionsKey(userPublicId)))).isFalse();
        assertThat(jdbcTemplate.queryForObject("select count(*) from auth_session_log where event_type = 'LOGOUT_ALL' and result = 'SUCCESS'", Long.class))
                .isEqualTo(1L);
    }
}