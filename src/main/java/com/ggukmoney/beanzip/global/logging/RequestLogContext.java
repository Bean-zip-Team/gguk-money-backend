package com.ggukmoney.beanzip.global.logging;

import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

public final class RequestLogContext {

    public static final String REQUEST_ID_HEADER = "X-Request-Id";
    public static final String REQUEST_ID_ATTRIBUTE = "requestId";

    private static final String DEFAULT_VALUE = "-";

    private RequestLogContext() {
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
