package com.ggukmoney.beanzip.global.logging;

import com.ggukmoney.beanzip.global.interceptor.AuthRequestAttributes;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.UUID;

public final class RequestLogContext {

    public static final String REQUEST_ID_HEADER = "X-Request-Id";
    public static final String REQUEST_ID_ATTRIBUTE = "requestId";

    private static final String DEFAULT_VALUE = "-";

    private RequestLogContext() {
    }

    /**
     * 접근 로그에 남길 주체를 채운다. 로그인·토큰 갱신은 인증이 필요 없는 경로라서 인터셉터가 주체를 채우지 못한다.
     * 그래서 토큰을 해석한 쪽이 직접 넣어야 요청이 실패했을 때도 누구의 요청인지 남는다.
     *
     * <p>인터셉터가 이미 채운 값은 덮어쓰지 않는다.
     */
    public static void putAuthenticatedPrincipal(UUID userId, UUID sessionId) {
        RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();

        if (!(requestAttributes instanceof ServletRequestAttributes servletRequestAttributes)) {
            return;
        }

        HttpServletRequest request = servletRequestAttributes.getRequest();
        putIfAbsent(request, AuthRequestAttributes.USER_ID, userId);
        putIfAbsent(request, AuthRequestAttributes.SESSION_ID, sessionId);
    }

    private static void putIfAbsent(HttpServletRequest request, String attributeName, Object value) {
        if (value != null && request.getAttribute(attributeName) == null) {
            request.setAttribute(attributeName, value);
        }
    }

    public static String currentRequestIdOrDefault() {
        RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();

        if (!(requestAttributes instanceof ServletRequestAttributes servletRequestAttributes)) {
            return DEFAULT_VALUE;
        }

        Object requestId = servletRequestAttributes.getRequest().getAttribute(REQUEST_ID_ATTRIBUTE);
        return requestId == null ? DEFAULT_VALUE : requestId.toString();
    }
}
