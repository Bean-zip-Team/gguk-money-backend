package com.ggukmoney.beanzip.global.logging;

import com.ggukmoney.beanzip.global.interceptor.AuthRequestAttributes;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class RequestLogContextTest {

    @AfterEach
    void clearRequestContext() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void putsPrincipalOnTheBoundRequestSoAccessLogCanReadIt() {
        MockHttpServletRequest request = bindRequest();
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();

        RequestLogContext.putAuthenticatedPrincipal(userId, sessionId);

        assertThat(request.getAttribute(AuthRequestAttributes.USER_ID)).isEqualTo(userId);
        assertThat(request.getAttribute(AuthRequestAttributes.SESSION_ID)).isEqualTo(sessionId);
    }

    @Test
    void keepsPrincipalAlreadySetByAuthInterceptor() {
        MockHttpServletRequest request = bindRequest();
        UUID interceptorUserId = UUID.randomUUID();
        UUID interceptorSessionId = UUID.randomUUID();
        request.setAttribute(AuthRequestAttributes.USER_ID, interceptorUserId);
        request.setAttribute(AuthRequestAttributes.SESSION_ID, interceptorSessionId);

        RequestLogContext.putAuthenticatedPrincipal(UUID.randomUUID(), UUID.randomUUID());

        assertThat(request.getAttribute(AuthRequestAttributes.USER_ID)).isEqualTo(interceptorUserId);
        assertThat(request.getAttribute(AuthRequestAttributes.SESSION_ID)).isEqualTo(interceptorSessionId);
    }

    @Test
    void ignoresMissingValues() {
        MockHttpServletRequest request = bindRequest();

        RequestLogContext.putAuthenticatedPrincipal(null, null);

        assertThat(request.getAttribute(AuthRequestAttributes.USER_ID)).isNull();
        assertThat(request.getAttribute(AuthRequestAttributes.SESSION_ID)).isNull();
    }

    @Test
    void doesNothingWhenNoRequestIsBound() {
        RequestContextHolder.resetRequestAttributes();

        assertThatCode(() -> RequestLogContext.putAuthenticatedPrincipal(UUID.randomUUID(), UUID.randomUUID()))
                .doesNotThrowAnyException();
    }

    private MockHttpServletRequest bindRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        return request;
    }
}
