package com.ggukmoney.beanzip.global.interceptor;

import com.ggukmoney.beanzip.domain.auth.infra.RedisAuthSessionRepository;
import com.ggukmoney.beanzip.domain.auth.model.AuthSession;
import com.ggukmoney.beanzip.domain.auth.service.AuthService;
import com.ggukmoney.beanzip.domain.auth.service.JwtTokenProvider;
import com.ggukmoney.beanzip.global.common.ApiPaths;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
@RequiredArgsConstructor
public class AuthInterceptor implements HandlerInterceptor {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    private final AuthService authService;
    private final RedisAuthSessionRepository authSessionRepository;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (isPublicEndpoint(request)) {
            return true;
        }
        authenticate(request);
        return true;
    }

    private void authenticate(HttpServletRequest request) {
        JwtTokenProvider.JwtTokenClaims claims = authService.parseAccessToken(resolveAccessToken(request));

        if (authSessionRepository.isAccessDenied(claims.jti())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "AUTH_ACCESS_DENIED");
        }

        authSessionRepository.findUserRevokedAtMillis(claims.userPublicId()).ifPresent(revokedAtMillis -> {
            if (claims.issuedAtMillis() <= revokedAtMillis) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "AUTH_USER_REVOKED");
            }
        });

        AuthSession session = authSessionRepository.findBySessionId(claims.sessionId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "AUTH_SESSION_NOT_FOUND"));

        if (!claims.userPublicId().equals(session.userPublicId())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "AUTH_SESSION_USER_MISMATCH");
        }

        request.setAttribute(AuthRequestAttributes.USER_PUBLIC_ID, claims.userPublicId());
        request.setAttribute(AuthRequestAttributes.SESSION_ID, claims.sessionId());
        request.setAttribute(AuthRequestAttributes.DEVICE_PUBLIC_ID, session.devicePublicId());
        request.setAttribute(AuthRequestAttributes.ACCESS_TOKEN_JTI, claims.jti());
        request.setAttribute(AuthRequestAttributes.ACCESS_TOKEN_EXPIRES_AT, claims.expiresAt());
    }

    private boolean isPublicEndpoint(HttpServletRequest request) {
        String method = request.getMethod();
        String path = request.getServletPath();
        if (!StringUtils.hasText(path)) {
            path = request.getRequestURI();
        }

        if ("OPTIONS".equalsIgnoreCase(method)
                || "/error".equals(path)
                || "/swagger-ui.html".equals(path)
                || PATH_MATCHER.match("/swagger-ui/**", path)
                || PATH_MATCHER.match("/v3/api-docs/**", path)) {
            return true;
        }

        return "POST".equalsIgnoreCase(method)
                && (ApiPaths.GUESTS.equals(path)
                || (ApiPaths.AUTH + "/refresh").equals(path)
                || (ApiPaths.AUTH + "/toss/login").equals(path));
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
