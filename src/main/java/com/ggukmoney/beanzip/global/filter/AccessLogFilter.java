package com.ggukmoney.beanzip.global.filter;

import com.ggukmoney.beanzip.global.interceptor.AuthRequestAttributes;
import com.ggukmoney.beanzip.global.logging.RequestLogContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerMapping;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.UUID;
import java.util.regex.Pattern;

@Slf4j
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class AccessLogFilter extends OncePerRequestFilter {

    private static final String ACCESS_LOG_PREFIX = "ACCESS";
    private static final String FORWARDED_FOR_HEADER = "X-Forwarded-For";
    private static final String DEFAULT_VALUE = "-";
    private static final int MAX_REQUEST_ID_LENGTH = 80;
    private static final int INTERNAL_SERVER_ERROR_STATUS = 500;
    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();
    private static final Pattern UNSAFE_REQUEST_ID_CHARACTERS = Pattern.compile("[^A-Za-z0-9._:-]");
    private static final Pattern CONTROL_CHARACTERS = Pattern.compile("[\\r\\n\\t]");
    private static final Pattern STATIC_RESOURCE_PATTERN = Pattern.compile(
            ".+\\.(css|js|map|png|jpg|jpeg|gif|svg|ico|webp|woff|woff2|ttf)$",
            Pattern.CASE_INSENSITIVE
    );

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String method = request.getMethod();
        String path = request.getRequestURI();

        return "OPTIONS".equalsIgnoreCase(method)
                || "/error".equals(path)
                || "/swagger-ui.html".equals(path)
                || PATH_MATCHER.match("/swagger-ui/**", path)
                || PATH_MATCHER.match("/v3/api-docs/**", path)
                || PATH_MATCHER.match("/webjars/**", path)
                || "/favicon.ico".equals(path)
                || STATIC_RESOURCE_PATTERN.matcher(path).matches();
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String requestId = resolveRequestId(request);
        request.setAttribute(RequestLogContext.REQUEST_ID_ATTRIBUTE, requestId);
        response.setHeader(RequestLogContext.REQUEST_ID_HEADER, requestId);

        long startNanos = System.nanoTime();
        boolean failed = false;

        try {
            filterChain.doFilter(request, response);
        } catch (ServletException | IOException | RuntimeException exception) {
            failed = true;
            throw exception;
        } finally {
            writeAccessLog(request, response, startNanos, failed);
        }
    }

    public String resolveSessionIdForLog(HttpServletRequest request) {
        String sessionId = resolveAttribute(request, AuthRequestAttributes.SESSION_ID, 128);
        return DEFAULT_VALUE.equals(sessionId) ? DEFAULT_VALUE : hashForLog(sessionId);
    }

    public String resolveDevicePublicIdForLog(HttpServletRequest request) {
        return resolveAttribute(request, AuthRequestAttributes.DEVICE_PUBLIC_ID, 128);
    }

    private void writeAccessLog(
            HttpServletRequest request,
            HttpServletResponse response,
            long startNanos,
            boolean failed
    ) {
        long durationMs = (System.nanoTime() - startNanos) / 1_000_000;
        int status = resolveStatus(response, failed);

        log.info(
                "{} requestId={} method={} pathTemplate={} status={} durationMs={} userId={} sessionIdHash={} devicePublicId={} clientIpMasked={} userAgent={} errorCode={}",
                ACCESS_LOG_PREFIX,
                resolveAttribute(request, RequestLogContext.REQUEST_ID_ATTRIBUTE, MAX_REQUEST_ID_LENGTH),
                request.getMethod(),
                resolvePathTemplate(request),
                status,
                durationMs,
                resolveAttribute(request, AuthRequestAttributes.USER_ID, 128),
                resolveSessionIdForLog(request),
                resolveDevicePublicIdForLog(request),
                maskClientIp(resolveClientIp(request)),
                sanitize(request.getHeader("User-Agent"), 256),
                resolveAttribute(request, AuthRequestAttributes.ERROR_CODE, 80)
        );
    }

    private int resolveStatus(HttpServletResponse response, boolean failed) {
        int status = response.getStatus();
        return failed && status < INTERNAL_SERVER_ERROR_STATUS ? INTERNAL_SERVER_ERROR_STATUS : status;
    }

    private String resolvePathTemplate(HttpServletRequest request) {
        Object pattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        return pattern == null ? request.getRequestURI() : sanitize(pattern.toString(), 256);
    }

    private String resolveRequestId(HttpServletRequest request) {
        String requestId = request.getHeader(RequestLogContext.REQUEST_ID_HEADER);
        if (!StringUtils.hasText(requestId)) {
            return UUID.randomUUID().toString();
        }
        String sanitized = UNSAFE_REQUEST_ID_CHARACTERS.matcher(requestId.trim()).replaceAll("");
        return StringUtils.hasText(sanitized) ? truncate(sanitized, MAX_REQUEST_ID_LENGTH) : UUID.randomUUID().toString();
    }

    private String resolveAttribute(HttpServletRequest request, String attributeName, int maxLength) {
        Object value = request.getAttribute(attributeName);
        return value == null ? DEFAULT_VALUE : sanitize(value.toString(), maxLength);
    }

    private String resolveClientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader(FORWARDED_FOR_HEADER);
        if (StringUtils.hasText(forwardedFor)) {
            return sanitize(forwardedFor.split(",", 2)[0], 80);
        }
        return sanitize(request.getRemoteAddr(), 80);
    }

    private String maskClientIp(String clientIp) {
        if (!StringUtils.hasText(clientIp) || DEFAULT_VALUE.equals(clientIp)) {
            return DEFAULT_VALUE;
        }
        if (clientIp.contains(".")) {
            return clientIp.replaceFirst("\\.\\d+$", ".0");
        }
        int separator = clientIp.lastIndexOf(':');
        return separator < 0 ? DEFAULT_VALUE : clientIp.substring(0, separator) + ":****";
    }

    private String sanitize(String value, int maxLength) {
        if (!StringUtils.hasText(value)) {
            return DEFAULT_VALUE;
        }
        String sanitized = CONTROL_CHARACTERS.matcher(value.trim()).replaceAll("");
        return StringUtils.hasText(sanitized) ? truncate(sanitized, maxLength) : DEFAULT_VALUE;
    }

    private String truncate(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private String hashForLog(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest).substring(0, 16);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 알고리즘을 찾을 수 없습니다.", exception);
        }
    }
}
