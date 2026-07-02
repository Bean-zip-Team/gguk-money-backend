package com.ggukmoney.beanzip.domain.auth.service;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

public class JwtTokenProviderTest {

    private final Clock clock = Clock.fixed(Instant.parse("2026-07-02T00:00:00Z"), ZoneOffset.UTC);
    private final JwtTokenProvider jwtTokenProvider = new JwtTokenProvider(
            new ObjectMapper(),
            "test-secret-test-secret-test-secret",
            "ggukmoney",
            clock
    );

    @Test
    void createsAccessTokenWithGgukmoneyClaims() {
        UUID sessionId = UUID.randomUUID();

        String token = jwtTokenProvider.createAccessToken("usr_public_1", sessionId, "access-jti-1");

        JwtTokenProvider.JwtTokenClaims claims = jwtTokenProvider.parseToken(token);

        assertThat(claims.userPublicId()).isEqualTo("usr_public_1");
        assertThat(claims.sessionId()).isEqualTo(sessionId);
        assertThat(claims.jti()).isEqualTo("access-jti-1");
        assertThat(claims.type()).isEqualTo("ACCESS");
        assertThat(claims.issuedAtMillis()).isEqualTo(Instant.parse("2026-07-02T00:00:00Z").toEpochMilli());
        assertThat(claims.expiresAt()).isEqualTo(Instant.parse("2026-07-02T00:15:00Z"));
    }

    @Test
    void createsRefreshTokenWithThirtyDayExpiration() {
        UUID sessionId = UUID.randomUUID();

        String token = jwtTokenProvider.createRefreshToken("usr_public_1", sessionId, "refresh-jti-1");

        JwtTokenProvider.JwtTokenClaims claims = jwtTokenProvider.parseToken(token);

        assertThat(claims.type()).isEqualTo("REFRESH");
        assertThat(claims.jti()).isEqualTo("refresh-jti-1");
        assertThat(claims.expiresAt()).isEqualTo(Instant.parse("2026-08-01T00:00:00Z"));
    }
}
