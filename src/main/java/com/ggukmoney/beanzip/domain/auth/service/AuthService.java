package com.ggukmoney.beanzip.domain.auth.service;

import com.ggukmoney.beanzip.domain.auth.audit.AuthAuditEventType;
import com.ggukmoney.beanzip.domain.auth.audit.AuthAuditResult;
import com.ggukmoney.beanzip.domain.auth.audit.AuthAuditService;
import com.ggukmoney.beanzip.domain.auth.dto.request.RefreshTokenRequest;
import com.ggukmoney.beanzip.domain.auth.dto.response.AuthTokenResponse;
import com.ggukmoney.beanzip.domain.auth.dto.response.LogoutAllResponse;
import com.ggukmoney.beanzip.domain.auth.dto.response.LogoutResponse;
import com.ggukmoney.beanzip.domain.auth.infra.RedisAuthSessionRepository;
import com.ggukmoney.beanzip.domain.auth.model.AuthSession;
import com.ggukmoney.beanzip.domain.auth.model.RefreshRotationResult;
import com.ggukmoney.beanzip.domain.auth.util.TokenHash;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final String TOKEN_TYPE = "Bearer";
    private static final String ACCESS_TYPE = "ACCESS";
    private static final String REFRESH_TYPE = "REFRESH";

    private final JwtTokenProvider jwtTokenProvider;
    private final RedisAuthSessionRepository authSessionRepository;
    private final AuthAuditService authAuditService;

    public AuthTokenResponse refresh(RefreshTokenRequest request) {
        String refreshToken = requireText(request.refreshToken(), "리프레시 토큰이 필요합니다.");
        JwtTokenProvider.JwtTokenClaims claims = parseRefreshToken(refreshToken);
        AuthSession session = authSessionRepository.findBySessionId(claims.sessionId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "AUTH_SESSION_NOT_FOUND"));

        String oldJtiHash = TokenHash.sha256Base64Url(claims.jti());
        String oldTokenHash = TokenHash.sha256Base64Url(refreshToken);
        String newRefreshJti = UUID.randomUUID().toString();
        String newAccessJti = UUID.randomUUID().toString();
        String newRefreshToken = jwtTokenProvider.createRefreshToken(session.userPublicId(), session.sessionId(), newRefreshJti);
        String newAccessToken = jwtTokenProvider.createAccessToken(session.userPublicId(), session.sessionId(), newAccessJti);
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
            authAuditService.record(
                    session.userPublicId(),
                    session.devicePublicId(),
                    session.sessionId(),
                    session.tokenFamilyIdHash(),
                    AuthAuditEventType.REFRESHED,
                    AuthAuditResult.SUCCESS,
                    null,
                    null
            );
            return new AuthTokenResponse(
                    session.userPublicId(),
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
            authAuditService.record(
                    session.userPublicId(),
                    session.devicePublicId(),
                    session.sessionId(),
                    session.tokenFamilyIdHash(),
                    AuthAuditEventType.REFRESH_REUSE_DETECTED,
                    AuthAuditResult.DENIED,
                    "AUTH_REFRESH_REUSED",
                    null
            );
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "AUTH_REFRESH_REUSED");
        }

        if (result == RefreshRotationResult.CONFLICT) {
            authAuditService.record(
                    session.userPublicId(),
                    session.devicePublicId(),
                    session.sessionId(),
                    session.tokenFamilyIdHash(),
                    AuthAuditEventType.REFRESH_CONFLICT,
                    AuthAuditResult.DENIED,
                    "AUTH_REFRESH_CONFLICT",
                    null
            );
            throw new ResponseStatusException(HttpStatus.CONFLICT, "AUTH_REFRESH_CONFLICT");
        }

        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "AUTH_SESSION_NOT_FOUND");
    }

    public LogoutResponse logoutCurrentSession(
            String currentUserPublicId,
            UUID currentSessionId,
            String currentAccessJti,
            Instant currentAccessExpiresAt,
            String optionalRefreshToken
    ) {
        AuthSession currentSession = authSessionRepository.findBySessionId(currentSessionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "AUTH_SESSION_NOT_FOUND"));

        if (!currentUserPublicId.equals(currentSession.userPublicId())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "AUTH_SESSION_USER_MISMATCH");
        }

        validateOptionalLogoutRefreshToken(optionalRefreshToken, currentSession);

        if (StringUtils.hasText(currentAccessJti) && currentAccessExpiresAt != null) {
            authSessionRepository.addAccessDeny(currentAccessJti, currentAccessExpiresAt);
        }
        authSessionRepository.deleteSession(currentSessionId);
        authAuditService.record(
                currentSession.userPublicId(),
                currentSession.devicePublicId(),
                currentSession.sessionId(),
                currentSession.tokenFamilyIdHash(),
                AuthAuditEventType.LOGOUT,
                AuthAuditResult.SUCCESS,
                null,
                null
        );
        return new LogoutResponse(true);
    }

    public LogoutAllResponse logoutAll(String userPublicId, String accessJti, Instant accessExpiresAt) {
        long revokedSessionCount = authSessionRepository.revokeAllUserSessions(
                userPublicId,
                accessJti,
                accessExpiresAt,
                Instant.now(),
                "LOGOUT_ALL"
        );
        authAuditService.record(
                userPublicId,
                null,
                null,
                null,
                AuthAuditEventType.LOGOUT_ALL,
                AuthAuditResult.SUCCESS,
                null,
                null
        );
        return new LogoutAllResponse(true, revokedSessionCount);
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
        boolean sameUser = currentSession.userPublicId().equals(refreshClaims.userPublicId());
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
