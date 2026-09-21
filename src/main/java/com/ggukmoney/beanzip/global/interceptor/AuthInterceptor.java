package com.ggukmoney.beanzip.global.interceptor;

import com.ggukmoney.beanzip.domain.auth.service.AuthService;
import com.ggukmoney.beanzip.domain.auth.service.JwtTokenProvider;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Component
@RequiredArgsConstructor
public class AuthInterceptor implements HandlerInterceptor {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String OPS_PATH_PREFIX = "/api/ops/";
    private static final String OPS_TOKEN_HEADER = "X-Ops-Token";
    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    private final AuthService authService;

    /**
     * 운영 조회용 토큰. 이 저장소에는 역할(role) 개념이 없어서 유저 토큰만으로는 운영 API 를
     * 가릴 수 없다 — 아무 유저나 자기 토큰으로 지급 현황을 보게 된다.
     *
     * <p>기본값이 비어 있고, 비어 있으면 전부 401 이다. 설정을 빠뜨리면 열리는 게 아니라
     * 닫힌다(fail-closed).
     */
    @Value("${app.ops.token:}")
    private String opsToken;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (isPublicEndpoint(request)) {
            return true;
        }
        if (isOpsEndpoint(request)) {
            // 운영 API 는 유저 세션이 아니라 별도 토큰으로 가른다.
            authenticateOps(request);
            return true;
        }
        authenticate(request);
        return true;
    }

    private boolean isOpsEndpoint(HttpServletRequest request) {
        return resolvePath(request).startsWith(OPS_PATH_PREFIX);
    }

    private void authenticateOps(HttpServletRequest request) {
        if (!StringUtils.hasText(opsToken)) {
            // 토큰이 설정되지 않았다. 무인증으로 열지 않는다.
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "OPS_TOKEN_NOT_CONFIGURED");
        }
        String presented = request.getHeader(OPS_TOKEN_HEADER);
        if (!StringUtils.hasText(presented) || !constantTimeEquals(presented, opsToken)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "OPS_TOKEN_INVALID");
        }
    }

    /** 길이·내용이 응답 시간으로 새지 않게 한다. */
    private static boolean constantTimeEquals(String presented, String expected) {
        return MessageDigest.isEqual(
                presented.getBytes(StandardCharsets.UTF_8),
                expected.getBytes(StandardCharsets.UTF_8));
    }

    private void authenticate(HttpServletRequest request) {
        JwtTokenProvider.JwtTokenClaims claims = authService.parseAccessToken(resolveAccessToken(request));

        if (authService.isAccessDenied(claims.jti())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "AUTH_ACCESS_DENIED");
        }

        authService.findUserRevokedAtMillis(claims.userId()).ifPresent(revokedAtMillis -> {
            if (claims.issuedAtMillis() <= revokedAtMillis) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "AUTH_USER_REVOKED");
            }
        });

        AuthService.AuthSession session = authService.findBySessionId(claims.sessionId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "AUTH_SESSION_NOT_FOUND"));

        if (!claims.userId().equals(session.userId())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "AUTH_SESSION_USER_MISMATCH");
        }

        request.setAttribute(AuthRequestAttributes.USER_ID, claims.userId());
        request.setAttribute(AuthRequestAttributes.SESSION_ID, claims.sessionId());
        request.setAttribute(AuthRequestAttributes.DEVICE_PUBLIC_ID, session.devicePublicId());
        request.setAttribute(AuthRequestAttributes.ACCESS_TOKEN_JTI, claims.jti());
        request.setAttribute(AuthRequestAttributes.ACCESS_TOKEN_EXPIRES_AT, claims.expiresAt());
    }

    private static String resolvePath(HttpServletRequest request) {
        String path = request.getServletPath();
        return StringUtils.hasText(path) ? path : request.getRequestURI();
    }

    private boolean isPublicEndpoint(HttpServletRequest request) {
        String method = request.getMethod();
        String path = resolvePath(request);

        if ("OPTIONS".equalsIgnoreCase(method)
                || "/error".equals(path)
                || "/swagger-ui.html".equals(path)
                || PATH_MATCHER.match("/swagger-ui/**", path)
                || PATH_MATCHER.match("/v3/api-docs/**", path)) {
            return true;
        }

        return "POST".equalsIgnoreCase(method)
                && ("/api/guests".equals(path)
                || "/api/auth/refresh".equals(path)
                || "/api/auth/toss/login".equals(path)
                || "/api/auth/toss/unlink-webhook".equals(path)
                || "/api/onboarding/keycap-boxes/open".equals(path));
    }

    private String resolveAccessToken(HttpServletRequest request) {
        String authorizationHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (!StringUtils.hasText(authorizationHeader) || !authorizationHeader.startsWith(BEARER_PREFIX)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "AUTH_REQUIRED");
        }

        String accessToken = authorizationHeader.substring(BEARER_PREFIX.length()).trim();
        if (!StringUtils.hasText(accessToken)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "AUTH_REQUIRED");
        }
        return accessToken;
    }
}
