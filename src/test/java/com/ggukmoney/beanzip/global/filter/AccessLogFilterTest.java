package com.ggukmoney.beanzip.global.filter;

import com.ggukmoney.beanzip.global.interceptor.AuthRequestAttributes;
import com.ggukmoney.beanzip.global.logging.RequestLogContext;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

public class AccessLogFilterTest {

    private final AccessLogFilter filter = new AccessLogFilter();

    @Test
    void usesRequestIdHeaderAndResponseHeader() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/me");
        request.addHeader(RequestLogContext.REQUEST_ID_HEADER, "request-123");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(request.getAttribute(RequestLogContext.REQUEST_ID_ATTRIBUTE)).isEqualTo("request-123");
        assertThat(response.getHeader(RequestLogContext.REQUEST_ID_HEADER)).isEqualTo("request-123");
        assertThat(response.getHeader("X-" + "Trace-Id")).isNull();
    }

    @Test
    void masksSessionAndDeviceValuesBeforeLogging() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/me");
        request.setAttribute(AuthRequestAttributes.SESSION_ID, "session-raw-value");
        request.setAttribute(AuthRequestAttributes.DEVICE_PUBLIC_ID, "device-public-id");

        assertThat(filter.resolveSessionIdForLog(request)).isNotEqualTo("session-raw-value");
        assertThat(filter.resolveSessionIdForLog(request)).hasSize(16);
        assertThat(filter.resolveDevicePublicIdForLog(request)).isEqualTo("device-public-id");
    }
}
