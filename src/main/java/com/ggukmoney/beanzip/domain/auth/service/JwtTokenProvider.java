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
import java.util.UUID;

@Service
public class JwtTokenProvider {

    private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder URL_DECODER = Base64.getUrlDecoder();
    private static final long ACCESS_TOKEN_TTL_SECONDS = 15 * 60;
    private static final long REFRESH_TOKEN_TTL_SECONDS = 30L * 24 * 60 * 60;

    private final ObjectMapper objectMapper;
    private final String secret;
    private final String issuer;
    private final Clock clock;

    @Autowired
    public JwtTokenProvider(
            ObjectMapper objectMapper,
            @Value("${app.auth.jwt.secret:local-dev-secret-change-me}") String secret,
            @Value("${app.auth.jwt.issuer:ggukmoney}") String issuer
    ) {
        this(objectMapper, secret, issuer, Clock.systemUTC());
    }

    public JwtTokenProvider(ObjectMapper objectMapper, String secret, String issuer, Clock clock) {
        this.objectMapper = objectMapper;
        this.secret = secret;
        this.issuer = issuer;
        this.clock = clock;
    }

    public String createAccessToken(String userPublicId, UUID sessionId, String jti) {
        Instant issuedAt = clock.instant();
        return createToken(userPublicId, sessionId, "ACCESS", jti, issuedAt, issuedAt.plusSeconds(ACCESS_TOKEN_TTL_SECONDS));
    }

    public String createRefreshToken(String userPublicId, UUID sessionId, String jti) {
        Instant issuedAt = clock.instant();
        return createToken(userPublicId, sessionId, "REFRESH", jti, issuedAt, issuedAt.plusSeconds(REFRESH_TOKEN_TTL_SECONDS));
    }

    public JwtTokenClaims parseToken(String token) {
        validateSecretConfigured();
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
                requiredStringClaim(claims, "sub"),
                UUID.fromString(requiredStringClaim(claims, "sid")),
                requiredStringClaim(claims, "jti"),
                requiredStringClaim(claims, "type"),
                requiredLongClaim(claims, "iat"),
                requiredLongClaim(claims, "issuedAtMillis"),
                Instant.ofEpochSecond(requiredLongClaim(claims, "exp"))
        );
    }

    private String createToken(
            String userPublicId,
            UUID sessionId,
            String type,
            String jti,
            Instant issuedAt,
            Instant expiresAt
    ) {
        validateSecretConfigured();

        try {
            String encodedHeader = encodeJson(Map.of("alg", "HS256", "typ", "JWT"));
            Map<String, Object> claims = new LinkedHashMap<>();
            claims.put("iss", issuer);
            claims.put("sub", requireText(userPublicId, "사용자 public id가 필요합니다."));
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

    private String requireText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        }
        return value.trim();
    }

    private void validateSecretConfigured() {
        if (!StringUtils.hasText(secret)) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "JWT_SECRET_REQUIRED");
        }
    }

    public record JwtTokenClaims(
            String userPublicId,
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
