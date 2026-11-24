package com.ggukmoney.beanzip.domain.friend.controller;

import com.ggukmoney.beanzip.domain.auth.service.AuthService;
import com.ggukmoney.beanzip.domain.auth.service.JwtTokenProvider;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.when;

/** 친구·초대·응원 컨트롤러 테스트가 같이 쓰는 인증 준비. */
final class FriendControllerTestSupport {

    static final String TOKEN = "access-token";

    private FriendControllerTestSupport() {
    }

    /** {@link #TOKEN} 을 보내면 인증을 통과하게 한다. 그 토큰의 사용자 ID 를 돌려준다. */
    static UUID authenticate(AuthService authService) {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        Instant now = Instant.now();
        when(authService.parseAccessToken(TOKEN)).thenReturn(new JwtTokenProvider.JwtTokenClaims(
                userId, sessionId, "access-jti", "access", now.getEpochSecond(), now.toEpochMilli(), now.plusSeconds(300)));
        when(authService.isAccessDenied("access-jti")).thenReturn(false);
        when(authService.findUserRevokedAtMillis(userId)).thenReturn(Optional.empty());
        when(authService.findBySessionId(sessionId)).thenReturn(Optional.of(new AuthService.AuthSession(
                sessionId, userId, "device-public-id", "refresh-jti-hash", "refresh-token-hash",
                "token-family-id-hash", null, null, now, now.plusSeconds(3600), "ACTIVE")));
        return userId;
    }
}
