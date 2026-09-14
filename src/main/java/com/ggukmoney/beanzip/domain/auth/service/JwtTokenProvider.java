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
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class JwtTokenProvider {

    private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder URL_DECODER = Base64.getUrlDecoder();
    private static final long ACCESS_TOKEN_TTL_SECONDS = 15 * 60;
    private static final long REFRESH_TOKEN_TTL_SECONDS = 30L * 24 * 60 * 60;
    // 로그인 풀림 재현(BEA-306)을 위한 임시 설정: 이 사용자만 토큰을 짧게 발급한다. 디버깅이 끝나면 제거한다.
    private static final Set<UUID> SHORT_TTL_DEBUG_USER_IDS = Set.of(
            UUID.fromString("7dc0edad-f5ed-41a9-b828-4167c6646569"),
            UUID.fromString("c115f184-fbaf-4ec0-9fe0-66734c985b57")
    );
    private static final long DEBUG_ACCESS_TOKEN_TTL_SECONDS = 60;
    private static final long DEBUG_REFRESH_TOKEN_TTL_SECONDS = 10 * 60;
    private static final String FORMER_LOCAL_DEFAULT_SECRET = "local-dev-secret" + "-change-me";

    private final ObjectMapper objectMapper;
    private final String secret;
    private final String issuer;
    private final Clock clock;

    @Autowired
    public JwtTokenProvider(
            ObjectMapper objectMapper,
            @Value("${app.auth.jwt.secret}") String secret,
            @Value("${app.auth.jwt.issuer:ggukmoney}") String issuer
    ) {
        this(objectMapper, secret, issuer, Clock.systemUTC());
    }

    public JwtTokenProvider(ObjectMapper objectMapper, String secret, String issuer, Clock clock) {
        this.objectMapper = objectMapper;
        this.secret = validateSecret(secret);
        this.issuer = issuer;
        this.clock = clock;
    }

    public String createAccessToken(UUID userId, UUID sessionId, String jti) {
        Instant issuedAt = clock.instant();
        return createToken(userId, sessionId, "ACCESS", jti, issuedAt, issuedAt.plusSeconds(accessTokenTtlSeconds(userId)));
    }

    public String createRefreshToken(UUID userId, UUID sessionId, String jti) {
        Instant issuedAt = clock.instant();
        return createToken(userId, sessionId, "REFRESH", jti, issuedAt, issuedAt.plusSeconds(refreshTokenTtlSeconds(userId)));
    }

    private static long accessTokenTtlSeconds(UUID userId) {
        return isShortTtlDebugUser(userId) ? DEBUG_ACCESS_TOKEN_TTL_SECONDS : ACCESS_TOKEN_TTL_SECONDS;
    }

    private static long refreshTokenTtlSeconds(UUID userId) {
        return isShortTtlDebugUser(userId) ? DEBUG_REFRESH_TOKEN_TTL_SECONDS : REFRESH_TOKEN_TTL_SECONDS;
    }

    // Set.of 는 null 조회 시 NPE 를 던지므로, 사용자 id 누락은 createToken 의 400 검증에 맡긴다.
    private static boolean isShortTtlDebugUser(UUID userId) {
        return userId != null && SHORT_TTL_DEBUG_USER_IDS.contains(userId);
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
