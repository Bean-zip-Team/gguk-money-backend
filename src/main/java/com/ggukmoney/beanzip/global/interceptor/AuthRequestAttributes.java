package com.ggukmoney.beanzip.global.interceptor;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.UUID;

public final class AuthRequestAttributes {

    public static final String USER_ID = "authenticatedUserId";
    public static final String SESSION_ID = "authenticatedSessionId";
    public static final String DEVICE_PUBLIC_ID = "authenticatedDevicePublicId";
    public static final String ACCESS_TOKEN_JTI = "authenticatedAccessTokenJti";
    public static final String ACCESS_TOKEN_EXPIRES_AT = "authenticatedAccessTokenExpiresAt";
    public static final String ERROR_CODE = "errorCode";

    private AuthRequestAttributes() {
    }

    public static UUID getRequiredUserId(HttpServletRequest request) {
        UUID value = getOptionalUserId(request);
        if (value == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "AUTH_REQUIRED");
        }
        return value;
    }

    public static UUID getOptionalUserId(HttpServletRequest request) {
        Object value = request.getAttribute(USER_ID);
        if (value instanceof UUID uuid) {
            return uuid;
        }
        if (value instanceof String text) {
            return UUID.fromString(text);
        }
        return null;
    }

    public static UUID getRequiredSessionId(HttpServletRequest request) {
        UUID value = getOptionalSessionId(request);
        if (value == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "AUTH_REQUIRED");
        }
        return value;
    }

    public static UUID getOptionalSessionId(HttpServletRequest request) {
        Object value = request.getAttribute(SESSION_ID);
        if (value instanceof UUID uuid) {
            return uuid;
        }
        if (value instanceof String text) {
            return UUID.fromString(text);
        }
        return null;
    }

    public static String getOptionalString(HttpServletRequest request, String attributeName) {
        Object value = request.getAttribute(attributeName);
        return value == null ? null : value.toString();
    }

    public static Instant getOptionalInstant(HttpServletRequest request, String attributeName) {
        Object value = request.getAttribute(attributeName);
        if (value instanceof Instant instant) {
            return instant;
        }
        if (value instanceof String text) {
            return Instant.parse(text);
        }
        return null;
    }
}
