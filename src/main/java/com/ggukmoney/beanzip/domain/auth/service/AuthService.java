package com.ggukmoney.beanzip.domain.auth.service;

import com.ggukmoney.beanzip.domain.auth.client.TossAuthClient;
import com.ggukmoney.beanzip.domain.auth.dto.request.RefreshTokenRequest;
import com.ggukmoney.beanzip.domain.auth.dto.request.TossLoginRequest;
import com.ggukmoney.beanzip.domain.auth.dto.request.TossUnlinkWebhookRequest;
import com.ggukmoney.beanzip.domain.auth.dto.response.AuthTokenResponse;
import com.ggukmoney.beanzip.domain.auth.dto.response.LogoutAllResponse;
import com.ggukmoney.beanzip.domain.auth.dto.response.LogoutResponse;
import com.ggukmoney.beanzip.domain.auth.dto.response.TossUnlinkWebhookResponse;
import com.ggukmoney.beanzip.domain.auth.entity.AuthIdentity;
import com.ggukmoney.beanzip.domain.auth.repository.AuthIdentityRepository;
import com.ggukmoney.beanzip.global.service.RedisService;
import com.ggukmoney.beanzip.global.util.TokenHash;
import com.ggukmoney.beanzip.domain.keycap.service.KeycapBoxAccountService;
import com.ggukmoney.beanzip.domain.point.service.PointAccountService;
import com.ggukmoney.beanzip.domain.user.dto.request.UserWithdrawalRequest;
import com.ggukmoney.beanzip.domain.user.dto.response.UserWithdrawalResponse;
import com.ggukmoney.beanzip.domain.user.entity.AppUser;
import com.ggukmoney.beanzip.domain.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private static final String TOKEN_TYPE = "Bearer";
    private static final String ACCESS_TYPE = "ACCESS";
    private static final String REFRESH_TYPE = "REFRESH";

    private static final Duration ACCESS_REVOKE_TTL = Duration.ofMinutes(20);
    private static final long REFRESH_CONFLICT_GRACE_MILLIS = 2_000L;

    private static final RedisScript<Long> ROTATE_REFRESH_SCRIPT =
            RedisScript.of(new ClassPathResource("scripts/auth-rotate-refresh.lua"), Long.class);
    private static final RedisScript<Long> REVOKE_ALL_USER_SESSIONS_SCRIPT =
            RedisScript.of(new ClassPathResource("scripts/auth-revoke-all-sessions.lua"), Long.class);

    private final JwtTokenProvider jwtTokenProvider;
    private final RedisService redisService;
    private final TossAuthClient tossAuthClient;
    private final AuthIdentityRepository authIdentityRepository;
    private final UserService userService;
    private final PointAccountService pointAccountService;
    private final KeycapBoxAccountService keycapBoxAccountService;

    @Value("${app.auth.toss.webhook-secret:}")
    private String tossWebhookSecret;

    public record AuthSession(
            UUID sessionId,
            UUID userId,
            String devicePublicId,
            String currentRefreshJtiHash,
            String refreshTokenHash,
            String tokenFamilyIdHash,
            String previousRefreshJtiHash,
            Instant rotatedAt,
            Instant issuedAt,
            Instant expiresAt,
            String status
    ) {
    }

    public enum RefreshRotationResult {
        ROTATED,
        NOT_FOUND,
        CONFLICT,
        REUSED
    }

    @Transactional
    public AuthTokenResponse loginWithToss(TossLoginRequest request) {
        TossAuthClient.TossToken tossToken = tossAuthClient.generateToken(
                requireText(request.authorizationCode(), "TOSS_AUTHORIZATION_CODE_REQUIRED"),
                request.referrer()
        );
        TossAuthClient.TossLoginMe loginMe = tossAuthClient.loginMe(requireText(tossToken.accessToken(), "TOSS_ACCESS_TOKEN_MISSING"));
        String userKey = requireText(loginMe.userKey(), "TOSS_USER_KEY_MISSING");

        AuthIdentity identity = authIdentityRepository
                .findByProviderAndProviderUserId(AuthIdentity.Provider.TOSS, userKey)
                .orElse(null);

        boolean newUser = false;
        AppUser user;
        if (identity == null) {
            user = userService.createActive(loginMe.nickname(), loginMe.profileImageUrl());
            authIdentityRepository.save(AuthIdentity.toss(user, userKey));
            pointAccountService.createFor(user);
            keycapBoxAccountService.createFor(user);
            newUser = true;
        } else {
            user = identity.getUser();
            if (user.isWithdrawn()) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "ACCOUNT_WITHDRAWN");
            }
            user = userService.recordLogin(user, loginMe.nickname(), loginMe.profileImageUrl());
        }

        return issueSessionTokens(user.getId(), newUser);
    }

    public AuthTokenResponse refresh(RefreshTokenRequest request) {
        String refreshToken = requireText(request.refreshToken(), "리프레시 토큰이 필요합니다.");
        JwtTokenProvider.JwtTokenClaims claims = parseRefreshToken(refreshToken);
        AuthSession session = findBySessionId(claims.sessionId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "AUTH_SESSION_NOT_FOUND"));

        String oldJtiHash = TokenHash.sha256Base64Url(claims.jti());
        String oldTokenHash = TokenHash.sha256Base64Url(refreshToken);
        String newRefreshJti = UUID.randomUUID().toString();
        String newAccessJti = UUID.randomUUID().toString();
        String newRefreshToken = jwtTokenProvider.createRefreshToken(session.userId(), session.sessionId(), newRefreshJti);
        String newAccessToken = jwtTokenProvider.createAccessToken(session.userId(), session.sessionId(), newAccessJti);
        JwtTokenProvider.JwtTokenClaims newRefreshClaims = jwtTokenProvider.parseToken(newRefreshToken);
        JwtTokenProvider.JwtTokenClaims newAccessClaims = jwtTokenProvider.parseToken(newAccessToken);

        RefreshRotationResult result = rotateRefreshToken(
                session,
                oldJtiHash,
                oldTokenHash,
                TokenHash.sha256Base64Url(newRefreshJti),
                TokenHash.sha256Base64Url(newRefreshToken),
                Instant.now(),
                newRefreshClaims.expiresAt()
        );

        if (result == RefreshRotationResult.ROTATED) {
            return new AuthTokenResponse(
                    session.userId(),
                    newAccessToken,
                    newRefreshToken,
                    TOKEN_TYPE,
                    newAccessClaims.expiresAt(),
                    newRefreshClaims.expiresAt(),
                    false
            );
        }

        if (result == RefreshRotationResult.REUSED) {
            deleteSession(session.sessionId());
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "AUTH_REFRESH_REUSED");
        }

        if (result == RefreshRotationResult.CONFLICT) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "AUTH_REFRESH_CONFLICT");
        }

        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "AUTH_SESSION_NOT_FOUND");
    }

    public LogoutResponse logoutCurrentSession(
            UUID currentUserId,
            UUID currentSessionId,
            String currentAccessJti,
            Instant currentAccessExpiresAt,
            String optionalRefreshToken
    ) {
        AuthSession currentSession = findBySessionId(currentSessionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "AUTH_SESSION_NOT_FOUND"));

        if (!currentUserId.equals(currentSession.userId())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "AUTH_SESSION_USER_MISMATCH");
        }

        validateOptionalLogoutRefreshToken(optionalRefreshToken, currentSession);

        if (StringUtils.hasText(currentAccessJti) && currentAccessExpiresAt != null) {
            addAccessDeny(currentAccessJti, currentAccessExpiresAt);
        }
        deleteSession(currentSessionId);
        return new LogoutResponse(true);
    }

    public LogoutAllResponse logoutAll(UUID userId, String accessJti, Instant accessExpiresAt) {
        long revokedSessionCount = revokeAllUserSessions(
                userId,
                accessJti,
                accessExpiresAt,
                Instant.now(),
                "LOGOUT_ALL"
        );
        return new LogoutAllResponse(true, revokedSessionCount);
    }

    @Transactional
    public UserWithdrawalResponse withdrawCurrentUser(
            UUID userId,
            String accessJti,
            Instant accessExpiresAt,
            UserWithdrawalRequest request
    ) {
        AppUser user = userService.getById(userId);
        if (user.isWithdrawn()) {
            revokeAllUserSessions(userId, accessJti, accessExpiresAt, Instant.now(), "WITHDRAWAL");
            return new UserWithdrawalResponse(true);
        }

        AuthIdentity identity = authIdentityRepository.findByUserIdAndProvider(userId, AuthIdentity.Provider.TOSS)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "TOSS_IDENTITY_NOT_FOUND"));
        TossAuthClient.TossToken tossToken = tossAuthClient.generateToken(
                requireText(request.authorizationCode(), "TOSS_AUTHORIZATION_CODE_REQUIRED"),
                request.referrer()
        );
        TossAuthClient.TossLoginMe loginMe = tossAuthClient.loginMe(requireText(tossToken.accessToken(), "TOSS_ACCESS_TOKEN_MISSING"));
        String userKey = requireText(loginMe.userKey(), "TOSS_USER_KEY_MISSING");
        if (!identity.getProviderUserId().equals(userKey)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "TOSS_USER_MISMATCH");
        }

        tossAuthClient.removeByUserKey(tossToken.accessToken(), userKey);
        userService.withdraw(user);
        revokeAllUserSessions(userId, accessJti, accessExpiresAt, Instant.now(), "WITHDRAWAL");
        return new UserWithdrawalResponse(true);
    }

    @Transactional
    public TossUnlinkWebhookResponse handleTossUnlinkWebhook(String authorization, TossUnlinkWebhookRequest request) {
        validateWebhookSecret(authorization);
        String eventType = requireText(request.referrer(), "TOSS_WEBHOOK_EVENT_REQUIRED");
        if (!eventType.equals("UNLINK") && !eventType.equals("WITHDRAWAL_TERMS") && !eventType.equals("WITHDRAWAL_TOSS")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "TOSS_WEBHOOK_UNSUPPORTED_EVENT");
        }

        authIdentityRepository.findByProviderAndProviderUserId(AuthIdentity.Provider.TOSS, requireText(request.userKey(), "TOSS_USER_KEY_MISSING"))
                .ifPresent(identity -> {
                    AppUser user = identity.getUser();
                    if (!user.isWithdrawn()) {
                        userService.withdraw(user);
                    }
                    revokeAllUserSessions(user.getId(), null, null, Instant.now(), "TOSS_UNLINK_WEBHOOK");
                });

        return new TossUnlinkWebhookResponse(true, eventType);
    }

    private AuthTokenResponse issueSessionTokens(UUID userId, boolean newUser) {
        UUID sessionId = UUID.randomUUID();
        String refreshJti = UUID.randomUUID().toString();
        String accessJti = UUID.randomUUID().toString();
        String accessToken = jwtTokenProvider.createAccessToken(userId, sessionId, accessJti);
        String refreshToken = jwtTokenProvider.createRefreshToken(userId, sessionId, refreshJti);
        JwtTokenProvider.JwtTokenClaims accessClaims = jwtTokenProvider.parseToken(accessToken);
        JwtTokenProvider.JwtTokenClaims refreshClaims = jwtTokenProvider.parseToken(refreshToken);
        AuthSession session = new AuthSession(
                sessionId,
                userId,
                "-",
                TokenHash.sha256Base64Url(refreshJti),
                TokenHash.sha256Base64Url(refreshToken),
                TokenHash.sha256Base64Url("family-" + sessionId),
                null,
                null,
                Instant.now(),
                refreshClaims.expiresAt(),
                "ACTIVE"
        );
        save(session);
        return new AuthTokenResponse(userId, accessToken, refreshToken, TOKEN_TYPE, accessClaims.expiresAt(), refreshClaims.expiresAt(), newUser);
    }

    private void validateWebhookSecret(String authorizationHeader) {
        String configuredSecret = requireText(tossWebhookSecret, "TOSS_WEBHOOK_SECRET_REQUIRED");
        String incomingSecret = decodeBasicSecret(authorizationHeader);
        if (!MessageDigest.isEqual(
                configuredSecret.getBytes(StandardCharsets.UTF_8),
                incomingSecret.getBytes(StandardCharsets.UTF_8)
        )) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "TOSS_WEBHOOK_UNAUTHORIZED");
        }
    }

    private String decodeBasicSecret(String authorizationHeader) {
        String normalized = requireText(authorizationHeader, "TOSS_WEBHOOK_UNAUTHORIZED");
        if (!normalized.regionMatches(true, 0, "Basic ", 0, 6)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "TOSS_WEBHOOK_UNAUTHORIZED");
        }
        try {
            return new String(Base64.getDecoder().decode(normalized.substring(6).trim()), StandardCharsets.UTF_8).trim();
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "TOSS_WEBHOOK_UNAUTHORIZED", exception);
        }
    }

    private JwtTokenProvider.JwtTokenClaims parseRefreshToken(String refreshToken) {
        JwtTokenProvider.JwtTokenClaims claims = jwtTokenProvider.parseToken(refreshToken);
        if (!REFRESH_TYPE.equals(claims.type())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "AUTH_REFRESH_REQUIRED");
        }
        if (!StringUtils.hasText(claims.jti())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "AUTH_INVALID_TOKEN");
        }
        if (claims.isExpiredAt(Instant.now())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "AUTH_REFRESH_EXPIRED");
        }
        return claims;
    }

    private void validateOptionalLogoutRefreshToken(String optionalRefreshToken, AuthSession currentSession) {
        if (!StringUtils.hasText(optionalRefreshToken)) {
            return;
        }

        JwtTokenProvider.JwtTokenClaims refreshClaims = parseRefreshToken(optionalRefreshToken);
        boolean sameUser = currentSession.userId().equals(refreshClaims.userId());
        boolean sameSession = currentSession.sessionId().equals(refreshClaims.sessionId());
        boolean sameCurrentJti = currentSession.currentRefreshJtiHash()
                .equals(TokenHash.sha256Base64Url(refreshClaims.jti()));
        boolean sameRefreshToken = currentSession.refreshTokenHash()
                .equals(TokenHash.sha256Base64Url(optionalRefreshToken));

        if (!sameUser || !sameSession || !sameCurrentJti || !sameRefreshToken) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "AUTH_LOGOUT_SESSION_MISMATCH");
        }
    }

    public JwtTokenProvider.JwtTokenClaims parseAccessToken(String accessToken) {
        JwtTokenProvider.JwtTokenClaims claims = jwtTokenProvider.parseToken(accessToken);
        if (!ACCESS_TYPE.equals(claims.type())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "AUTH_ACCESS_REQUIRED");
        }
        if (claims.isExpiredAt(Instant.now())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "AUTH_ACCESS_EXPIRED");
        }
        return claims;
    }

    private String requireText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        }
        return value.trim();
    }

    public void save(AuthSession session) {
        String refreshKey = refreshKey(session.sessionId());
        redisService.putAllHash(refreshKey, Map.of(
                "userId", session.userId().toString(),
                "devicePublicId", session.devicePublicId(),
                "currentRefreshJtiHash", session.currentRefreshJtiHash(),
                "refreshTokenHash", session.refreshTokenHash(),
                "tokenFamilyIdHash", session.tokenFamilyIdHash(),
                "previousRefreshJtiHash", valueOrEmpty(session.previousRefreshJtiHash()),
                "rotatedAt", valueOrEmpty(session.rotatedAt()),
                "issuedAt", session.issuedAt().toString(),
                "expiresAt", session.expiresAt().toString(),
                "status", session.status()
        ));
        redisService.expire(refreshKey, Duration.between(Instant.now(), session.expiresAt()));
        redisService.addToSortedSet(userSessionsKey(session.userId()), session.sessionId().toString(), session.expiresAt().toEpochMilli());
    }

    public Optional<AuthSession> findBySessionId(UUID sessionId) {
        Map<String, String> entries = redisService.getAllHash(refreshKey(sessionId));
        if (entries.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new AuthSession(
                sessionId,
                UUID.fromString(required(entries, "userId")),
                required(entries, "devicePublicId"),
                required(entries, "currentRefreshJtiHash"),
                required(entries, "refreshTokenHash"),
                required(entries, "tokenFamilyIdHash"),
                blankToNull(required(entries, "previousRefreshJtiHash")),
                parseNullableInstant(required(entries, "rotatedAt")),
                Instant.parse(required(entries, "issuedAt")),
                Instant.parse(required(entries, "expiresAt")),
                required(entries, "status")
        ));
    }

    public RefreshRotationResult rotateRefreshToken(
            AuthSession session,
            String expectedCurrentRefreshJtiHash,
            String expectedRefreshTokenHash,
            String newCurrentRefreshJtiHash,
            String newRefreshTokenHash,
            Instant rotatedAt,
            Instant expiresAt
    ) {
        Long result = redisService.executeScript(
                ROTATE_REFRESH_SCRIPT,
                List.of(refreshKey(session.sessionId()), userSessionsKey(session.userId())),
                expectedCurrentRefreshJtiHash,
                expectedRefreshTokenHash,
                newCurrentRefreshJtiHash,
                newRefreshTokenHash,
                rotatedAt.toString(),
                expiresAt.toString(),
                String.valueOf(expiresAt.toEpochMilli()),
                session.sessionId().toString(),
                String.valueOf(rotatedAt.toEpochMilli()),
                String.valueOf(REFRESH_CONFLICT_GRACE_MILLIS)
        );

        if (result == null || result == 0L) {
            return RefreshRotationResult.NOT_FOUND;
        }
        if (result == 1L) {
            return RefreshRotationResult.ROTATED;
        }
        if (result == 3L) {
            return RefreshRotationResult.REUSED;
        }
        return RefreshRotationResult.CONFLICT;
    }

    public void deleteSession(UUID sessionId) {
        findBySessionId(sessionId).ifPresent(session -> {
            redisService.delete(refreshKey(sessionId));
            redisService.removeFromSortedSet(userSessionsKey(session.userId()), sessionId.toString());
        });
    }

    public void addAccessDeny(String jti, Instant expiresAt) {
        String key = "auth:deny:access:" + jti;
        redisService.set(key, "1", Duration.between(Instant.now(), expiresAt));
    }

    public boolean isAccessDenied(String jti) {
        return redisService.exists("auth:deny:access:" + jti);
    }

    public long revokeAllUserSessions(
            UUID userId,
            String accessJti,
            Instant accessExpiresAt,
            Instant revokedAt,
            String reason
    ) {
        try {
            String userSessionsKey = userSessionsKey(userId);
            long revokedAtMillis = revokedAt.toEpochMilli();
            Long revokedCount = redisService.executeScript(
                    REVOKE_ALL_USER_SESSIONS_SCRIPT,
                    List.of(userSessionsKey, revokeUserKey(userId)),
                    String.valueOf(revokedAtMillis),
                    revokeMarker(revokedAtMillis, reason),
                    String.valueOf(ACCESS_REVOKE_TTL.toMillis()),
                    StringUtils.hasText(accessJti) ? accessJti : "",
                    accessExpiresAt == null ? "" : String.valueOf(accessExpiresAt.toEpochMilli())
            );
            if (revokedCount == null) {
                throw new IllegalStateException("Redis logout-all script returned null");
            }
            return revokedCount;
        } catch (RuntimeException exception) {
            log.error("Failed to revoke all Redis auth sessions for userId={}", userId, exception);
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "AUTH_REDIS_UNAVAILABLE", exception);
        }
    }

    public Optional<Long> findUserRevokedAtMillis(UUID userId) {
        return redisService.get(revokeUserKey(userId)).map(this::parseRevokedAtMillis);
    }

    public static String refreshKey(UUID sessionId) {
        return "auth:refresh:" + sessionId;
    }

    public static String userSessionsKey(UUID userId) {
        return "auth:user-sessions:" + userId;
    }

    private static String revokeUserKey(UUID userId) {
        return "auth:revoke:user:" + userId;
    }

    private static String revokeMarker(long revokedAtMillis, String reason) {
        return "{\"revokedAtMillis\":" + revokedAtMillis + ",\"reason\":\"" + reason + "\"}";
    }

    private long parseRevokedAtMillis(String value) {
        if (!value.startsWith("{")) {
            return Long.parseLong(value);
        }

        String fieldName = "\"revokedAtMillis\":";
        int fieldStart = value.indexOf(fieldName);
        if (fieldStart < 0) {
            throw new IllegalArgumentException("revokedAtMillis is missing");
        }
        int numberStart = fieldStart + fieldName.length();
        int numberEnd = value.indexOf(',', numberStart);
        if (numberEnd < 0) {
            numberEnd = value.indexOf('}', numberStart);
        }
        if (numberEnd < 0) {
            throw new IllegalArgumentException("revokedAtMillis is malformed");
        }
        return Long.parseLong(value.substring(numberStart, numberEnd));
    }

    private static String required(Map<String, String> entries, String key) {
        String value = entries.get(key);
        return value == null ? "" : value;
    }

    private static String valueOrEmpty(Object value) {
        return value == null ? "" : value.toString();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static Instant parseNullableInstant(String value) {
        return value == null || value.isBlank() ? null : Instant.parse(value);
    }
}
