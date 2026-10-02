package com.ggukmoney.beanzip.domain.auth.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class JwtTokenProvider {

    private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder URL_DECODER = Base64.getUrlDecoder();
    private static final Duration DEFAULT_ACCESS_TOKEN_TTL = Duration.ofMinutes(15);
    private static final Duration DEFAULT_REFRESH_TOKEN_TTL = Duration.ofDays(30);
    private static final String FORMER_LOCAL_DEFAULT_SECRET = "local-dev-secret" + "-change-me";

    private final ObjectMapper objectMapper;
    private final String secret;
    private final String issuer;
    private final Clock clock;
    private final Duration accessTokenTtl;
    private final Duration refreshTokenTtl;

    // 알파에서 만료·재발급을 짧게 시험할 수 있도록 유효 시간을 설정으로 연다. 기본값은 운영 값이다.
    @Autowired
    public JwtTokenProvider(
            ObjectMapper objectMapper,
            @Value("${app.auth.jwt.secret}") String secret,
            @Value("${app.auth.jwt.issuer:ggukmoney}") String issuer,
            @Value("${app.auth.jwt.access-token-ttl:15m}") Duration accessTokenTtl,
            @Value("${app.auth.jwt.refresh-token-ttl:30d}") Duration refreshTokenTtl
    ) {
        this(objectMapper, secret, issuer, Clock.systemUTC(), accessTokenTtl, refreshTokenTtl);
    }

    public JwtTokenProvider(ObjectMapper objectMapper, String secret, String issuer, Clock clock) {
        this(objectMapper, secret, issuer, clock, DEFAULT_ACCESS_TOKEN_TTL, DEFAULT_REFRESH_TOKEN_TTL);
    }

    public JwtTokenProvider(
            ObjectMapper objectMapper,
            String secret,
            String issuer,
            Clock clock,
            Duration accessTokenTtl,
            Duration refreshTokenTtl
    ) {
        this.objectMapper = objectMapper;
        this.secret = validateSecret(secret);
        this.issuer = issuer;
        this.clock = clock;
        this.accessTokenTtl = requirePositive(accessTokenTtl, "app.auth.jwt.access-token-ttl");
        this.refreshTokenTtl = requirePositive(refreshTokenTtl, "app.auth.jwt.refresh-token-ttl");
    }

    private static Duration requirePositive(Duration ttl, String property) {
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            throw new IllegalStateException(property + " must be positive");
        }
        return ttl;
    }

    public Duration accessTokenTtl() {
        return accessTokenTtl;
    }

    public String createAccessToken(UUID userId, UUID sessionId, String jti) {
        Instant issuedAt = clock.instant();
        return createToken(userId, sessionId, "ACCESS", jti, issuedAt, issuedAt.plus(accessTokenTtl));
    }

    public String createRefreshToken(UUID userId, UUID sessionId, String jti) {
        Instant issuedAt = clock.instant();
        return createToken(userId, sessionId, "REFRESH", jti, issuedAt, issuedAt.plus(refreshTokenTtl));
    }

    public JwtTokenClaims parseToken(String token) {
        String[] segments = splitToken(token);
        String unsignedToken = segments[0] + "." + segments[1];
        String expectedSignature = sign(unsignedToken);

        if (!MessageDigest.isEqual(
                expectedSignature.getBytes(StandardCharsets.UTF_8),
                segments[2].getBytes(StandardCharsets.UTF_8)
        )) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "AUTH_INVALID_TOKEN");
        }

        Map<String, Object> claims = readClaims(segments[1]);
        validateIssuer(claims.get("iss"));

        return new JwtTokenClaims(
                requiredUuidClaim(claims, "sub"),
                requiredUuidClaim(claims, "sid"),
                requiredStringClaim(claims, "jti"),
                requiredStringClaim(claims, "type"),
                requiredLongClaim(claims, "iat"),
                requiredLongClaim(claims, "issuedAtMillis"),
                Instant.ofEpochSecond(requiredLongClaim(claims, "exp"))
        );
    }

    private String createToken(
            UUID userId,
            UUID sessionId,
            String type,
            String jti,
            Instant issuedAt,
            Instant expiresAt
    ) {
        try {
            String encodedHeader = encodeJson(Map.of("alg", "HS256", "typ", "JWT"));
            Map<String, Object> claims = new LinkedHashMap<>();
            claims.put("iss", issuer);
            claims.put("sub", requireUuid(userId, "사용자 id가 필요합니다.").toString());
            claims.put("sid", sessionId.toString());
            claims.put("jti", requireText(jti, "토큰 jti가 필요합니다."));
            claims.put("type", type);
            claims.put("iat", issuedAt.getEpochSecond());
            claims.put("issuedAtMillis", issuedAt.toEpochMilli());
            claims.put("exp", expiresAt.getEpochSecond());

            String encodedPayload = encodeJson(claims);
            String unsignedToken = encodedHeader + "." + encodedPayload;
            return unsignedToken + "." + sign(unsignedToken);
        } catch (JacksonException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "JWT_CREATE_FAILED", exception);
        }
    }

    private String encodeJson(Map<String, Object> value) throws JacksonException {
        return URL_ENCODER.encodeToString(objectMapper.writeValueAsBytes(value));
    }

    private String[] splitToken(String token) {
        if (!StringUtils.hasText(token)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "AUTH_TOKEN_EMPTY");
        }
        String[] segments = token.split("\\.");
        if (segments.length != 3) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "AUTH_TOKEN_MALFORMED");
        }
        return segments;
    }

    private Map<String, Object> readClaims(String payloadSegment) {
        try {
            return objectMapper.readValue(URL_DECODER.decode(payloadSegment), new TypeReference<>() {
            });
        } catch (IllegalArgumentException | JacksonException exception) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "AUTH_TOKEN_PARSE_FAILED", exception);
        }
    }

    private String sign(String unsignedToken) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return URL_ENCODER.encodeToString(mac.doFinal(unsignedToken.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "JWT_SIGN_FAILED", exception);
        }
    }

    private void validateIssuer(Object issuerClaim) {
        String tokenIssuer = issuerClaim == null ? null : issuerClaim.toString();
        if (!StringUtils.hasText(tokenIssuer) || !issuer.equals(tokenIssuer)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "AUTH_INVALID_ISSUER");
        }
    }

    private String requiredStringClaim(Map<String, Object> claims, String key) {
        Object value = claims.get(key);
        if (value == null || !StringUtils.hasText(value.toString())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "AUTH_INVALID_CLAIM");
        }
        return value.toString();
    }

    private long requiredLongClaim(Map<String, Object> claims, String key) {
        Object value = claims.get(key);
        if (!(value instanceof Number number)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "AUTH_INVALID_CLAIM");
        }
        return number.longValue();
    }

    private UUID requiredUuidClaim(Map<String, Object> claims, String key) {
        try {
            return UUID.fromString(requiredStringClaim(claims, key));
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "AUTH_INVALID_CLAIM", exception);
        }
    }

    private String requireText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        }
        return value.trim();
    }

    private UUID requireUuid(UUID value, String message) {
        if (value == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        }
        return value;
    }

    private static String validateSecret(String secret) {
        if (!StringUtils.hasText(secret)) {
            throw new IllegalStateException("JWT_SECRET_REQUIRED");
        }
        String trimmed = secret.trim();
        if (FORMER_LOCAL_DEFAULT_SECRET.equals(trimmed)) {
            throw new IllegalStateException("JWT_SECRET_FORBIDDEN");
        }
        if (trimmed.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("JWT_SECRET_TOO_SHORT");
        }
        return trimmed;
    }

    public record JwtTokenClaims(
            UUID userId,
            UUID sessionId,
            String jti,
            String type,
            long issuedAtEpochSecond,
            long issuedAtMillis,
            Instant expiresAt
    ) {
        public boolean isExpiredAt(Instant now) {
            Instant targetTime = now == null ? Instant.now() : now;
            return !expiresAt.isAfter(targetTime);
        }
    }
}
