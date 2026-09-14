package com.ggukmoney.beanzip.domain.auth.service;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

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
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();

        String token = jwtTokenProvider.createAccessToken(userId, sessionId, "access-jti-1");

        JwtTokenProvider.JwtTokenClaims claims = jwtTokenProvider.parseToken(token);

        assertThat(claims.userId()).isEqualTo(userId);
        assertThat(claims.sessionId()).isEqualTo(sessionId);
        assertThat(claims.jti()).isEqualTo("access-jti-1");
        assertThat(claims.type()).isEqualTo("ACCESS");
        assertThat(claims.issuedAtMillis()).isEqualTo(Instant.parse("2026-07-02T00:00:00Z").toEpochMilli());
        assertThat(claims.expiresAt()).isEqualTo(Instant.parse("2026-07-02T00:15:00Z"));
    }

    @Test
    void createsRefreshTokenWithThirtyDayExpiration() {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();

        String token = jwtTokenProvider.createRefreshToken(userId, sessionId, "refresh-jti-1");

        JwtTokenProvider.JwtTokenClaims claims = jwtTokenProvider.parseToken(token);

        assertThat(claims.userId()).isEqualTo(userId);
        assertThat(claims.type()).isEqualTo("REFRESH");
        assertThat(claims.jti()).isEqualTo("refresh-jti-1");
        assertThat(claims.expiresAt()).isEqualTo(Instant.parse("2026-08-01T00:00:00Z"));
    }

    @Test
    void rejectsNonUuidSubjectClaim() {
        UUID userId = UUID.randomUUID();
        String token = jwtTokenProvider.createAccessToken(userId, UUID.randomUUID(), "access-jti-1");
        String[] segments = token.split("\\.");
        String payload = new String(Base64.getUrlDecoder().decode(segments[1]), StandardCharsets.UTF_8);
        String tamperedPayload = payload.replace(userId.toString(), "not-a-uuid");
        String encodedPayload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(tamperedPayload.getBytes(StandardCharsets.UTF_8));
        String unsignedToken = segments[0] + "." + encodedPayload;
        String signature = ReflectionTestUtils.invokeMethod(jwtTokenProvider, "sign", unsignedToken);

        assertThatThrownBy(() -> jwtTokenProvider.parseToken(unsignedToken + "." + signature))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(exception -> ((ResponseStatusException) exception).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void rejectsBlankSecret() {
        assertThatThrownBy(() -> new JwtTokenProvider(new ObjectMapper(), "   ", "ggukmoney", clock))
                .hasMessageContaining("JWT_SECRET_REQUIRED");
    }

    @Test
    void rejectsSecretShorterThanThirtyTwoUtf8Bytes() {
        assertThatThrownBy(() -> new JwtTokenProvider(new ObjectMapper(), "short-secret", "ggukmoney", clock))
                .hasMessageContaining("JWT_SECRET_TOO_SHORT");
    }

    @Test
    void rejectsFormerLocalDefaultSecret() {
        String formerLocalDefaultSecret = "local-dev-secret" + "-change-me";

        assertThatThrownBy(() -> new JwtTokenProvider(new ObjectMapper(), formerLocalDefaultSecret, "ggukmoney", clock))
                .hasMessageContaining("JWT_SECRET_FORBIDDEN");
    }
}
