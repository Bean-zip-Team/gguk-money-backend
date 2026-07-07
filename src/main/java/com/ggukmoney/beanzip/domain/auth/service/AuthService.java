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
import com.ggukmoney.beanzip.domain.auth.infra.RedisAuthSessionRepository;
import com.ggukmoney.beanzip.domain.auth.model.AuthSession;
import com.ggukmoney.beanzip.domain.auth.model.RefreshRotationResult;
import com.ggukmoney.beanzip.domain.auth.repository.AuthIdentityRepository;
import com.ggukmoney.beanzip.domain.auth.util.TokenHash;
import com.ggukmoney.beanzip.domain.keycap.entity.KeycapBoxAccount;
import com.ggukmoney.beanzip.domain.keycap.repository.KeycapBoxAccountRepository;
import com.ggukmoney.beanzip.domain.point.entity.PointAccount;
import com.ggukmoney.beanzip.domain.point.repository.PointAccountRepository;
import com.ggukmoney.beanzip.domain.user.dto.request.UserWithdrawalRequest;
import com.ggukmoney.beanzip.domain.user.dto.response.UserWithdrawalResponse;
import com.ggukmoney.beanzip.domain.user.entity.AppUser;
import com.ggukmoney.beanzip.domain.user.repository.AppUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final String TOKEN_TYPE = "Bearer";
    private static final String ACCESS_TYPE = "ACCESS";
    private static final String REFRESH_TYPE = "REFRESH";

    private final JwtTokenProvider jwtTokenProvider;
    private final RedisAuthSessionRepository authSessionRepository;
    private final TossAuthClient tossAuthClient;
    private final AppUserRepository appUserRepository;
    private final AuthIdentityRepository authIdentityRepository;
    private final PointAccountRepository pointAccountRepository;
    private final KeycapBoxAccountRepository keycapBoxAccountRepository;

    @Value("${app.auth.toss.webhook-secret:}")
    private String tossWebhookSecret;

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
            user = appUserRepository.save(AppUser.createActive(loginMe.nickname(), loginMe.profileImageUrl()));
            authIdentityRepository.save(AuthIdentity.toss(user, userKey));
            pointAccountRepository.save(PointAccount.createFor(user));
            keycapBoxAccountRepository.save(KeycapBoxAccount.createFor(user));
            newUser = true;
        } else {
            user = identity.getUser();
            if (user.isWithdrawn()) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "ACCOUNT_WITHDRAWN");
            }
            user.recordLogin(loginMe.nickname(), loginMe.profileImageUrl());
        }

        return issueSessionTokens(user.getId(), newUser);
    }

    public AuthTokenResponse refresh(RefreshTokenRequest request) {
        String refreshToken = requireText(request.refreshToken(), "리프레시 토큰이 필요합니다.");
        JwtTokenProvider.JwtTokenClaims claims = parseRefreshToken(refreshToken);
        AuthSession session = authSessionRepository.findBySessionId(claims.sessionId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "AUTH_SESSION_NOT_FOUND"));

        String oldJtiHash = TokenHash.sha256Base64Url(claims.jti());
        String oldTokenHash = TokenHash.sha256Base64Url(refreshToken);
        String newRefreshJti = UUID.randomUUID().toString();
        String newAccessJti = UUID.randomUUID().toString();
        String newRefreshToken = jwtTokenProvider.createRefreshToken(session.userId(), session.sessionId(), newRefreshJti);
        String newAccessToken = jwtTokenProvider.createAccessToken(session.userId(), session.sessionId(), newAccessJti);
        JwtTokenProvider.JwtTokenClaims newRefreshClaims = jwtTokenProvider.parseToken(newRefreshToken);
        JwtTokenProvider.JwtTokenClaims newAccessClaims = jwtTokenProvider.parseToken(newAccessToken);

        RefreshRotationResult result = authSessionRepository.rotateRefreshToken(
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
            authSessionRepository.deleteSession(session.sessionId());
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
        AuthSession currentSession = authSessionRepository.findBySessionId(currentSessionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "AUTH_SESSION_NOT_FOUND"));

        if (!currentUserId.equals(currentSession.userId())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "AUTH_SESSION_USER_MISMATCH");
        }

        validateOptionalLogoutRefreshToken(optionalRefreshToken, currentSession);

        if (StringUtils.hasText(currentAccessJti) && currentAccessExpiresAt != null) {
            authSessionRepository.addAccessDeny(currentAccessJti, currentAccessExpiresAt);
        }
        authSessionRepository.deleteSession(currentSessionId);
        return new LogoutResponse(true);
    }

    public LogoutAllResponse logoutAll(UUID userId, String accessJti, Instant accessExpiresAt) {
        long revokedSessionCount = authSessionRepository.revokeAllUserSessions(
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
        AppUser user = appUserRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "AUTH_USER_NOT_FOUND"));
        if (user.isWithdrawn()) {
            authSessionRepository.revokeAllUserSessions(userId, accessJti, accessExpiresAt, Instant.now(), "WITHDRAWAL");
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
        withdrawLocally(user);
        authSessionRepository.revokeAllUserSessions(userId, accessJti, accessExpiresAt, Instant.now(), "WITHDRAWAL");
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
                        withdrawLocally(user);
                    }
                    authSessionRepository.revokeAllUserSessions(user.getId(), null, null, Instant.now(), "TOSS_UNLINK_WEBHOOK");
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
        authSessionRepository.save(session);
        return new AuthTokenResponse(userId, accessToken, refreshToken, TOKEN_TYPE, accessClaims.expiresAt(), refreshClaims.expiresAt(), newUser);
    }

    private void withdrawLocally(AppUser user) {
        user.withdraw();
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
}
