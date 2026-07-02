package com.ggukmoney.beanzip.domain.auth.audit;

import com.ggukmoney.beanzip.domain.auth.util.TokenHash;
import com.ggukmoney.beanzip.support.FullStackIntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AuthAuditServiceIntegrationTest extends FullStackIntegrationTestSupport {

    @Autowired
    private AuthAuditService authAuditService;

    @Test
    void persistsAuditLogWithUuidEnumTraceAndJsonbMetadataInPostgres() {
        String userPublicId = UUID.randomUUID().toString();
        String devicePublicId = UUID.randomUUID().toString();
        UUID sessionId = UUID.randomUUID();

        authAuditService.record(
                userPublicId,
                devicePublicId,
                sessionId,
                "family-hash",
                AuthAuditEventType.REFRESHED,
                AuthAuditResult.SUCCESS,
                null,
                "{\"source\":\"integration\"}"
        );

        Map<String, Object> row = jdbcTemplate.queryForMap("select * from auth_session_log where user_public_id = ?::uuid", userPublicId);
        assertThat(row.get("public_id")).isNotNull();
        assertThat(row.get("device_public_id").toString()).isEqualTo(devicePublicId);
        assertThat(row.get("session_id_hash")).isEqualTo(TokenHash.sha256Base64Url(sessionId.toString()));
        assertThat(row.get("token_family_id_hash")).isEqualTo("family-hash");
        assertThat(row.get("event_type")).isEqualTo("REFRESHED");
        assertThat(row.get("result")).isEqualTo("SUCCESS");
        assertThat(row.get("trace_id")).isNotNull();
        assertThat(row.get("metadata").toString()).contains("source").contains("integration");
        assertThat(row.toString()).doesNotContain("Bearer ").doesNotContain("refresh-token").doesNotContain("authorizationCode");
    }

    @Test
    void auditWriteFailureIsSwallowedAndDoesNotPropagate() {
        authAuditService.record(
                UUID.randomUUID().toString(),
                UUID.randomUUID().toString(),
                UUID.randomUUID(),
                "family-hash",
                AuthAuditEventType.REFRESHED,
                AuthAuditResult.SUCCESS,
                null,
                "not-json"
        );

        assertThat(jdbcTemplate.queryForObject("select count(*) from auth_session_log", Long.class)).isZero();
    }
}