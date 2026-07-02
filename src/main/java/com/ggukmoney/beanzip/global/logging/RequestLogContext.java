package com.ggukmoney.beanzip.global.logging;

import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

public final class RequestLogContext {

    public static final String TRACE_ID_HEADER = "X-Trace-Id";
    public static final String TRACE_ID_ATTRIBUTE = "traceId";

    private static final String DEFAULT_VALUE = "-";

    private RequestLogContext() {
    }

    public static String currentTraceIdOrDefault() {
        RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();

        if (!(requestAttributes instanceof ServletRequestAttributes servletRequestAttributes)) {
            return DEFAULT_VALUE;
        }

        Object traceId = servletRequestAttributes.getRequest().getAttribute(TRACE_ID_ATTRIBUTE);
        return traceId == null ? DEFAULT_VALUE : traceId.toString();
    }
}
