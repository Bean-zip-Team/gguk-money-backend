package com.ggukmoney.beanzip.global.interceptor;

import com.ggukmoney.beanzip.domain.auth.service.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * 운영 API 는 유저 세션이 아니라 X-Ops-Token 으로 가른다 (BEA-272).
 * 이 저장소에는 역할 개념이 없어, 이 검사가 빠지면 아무 유저나 지급 현황을 본다.
 */
class AuthInterceptorOpsTest {

    private static final String OPS_TOKEN = "ops-secret-token";

    private final AuthService authService = mock(AuthService.class);
    private final AuthInterceptor interceptor = new AuthInterceptor(authService);
    private final MockHttpServletResponse response = new MockHttpServletResponse();

    private MockHttpServletRequest opsRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/ops/promotions/grants");
        request.setServletPath("/api/ops/promotions/grants");
        return request;
    }

    @Test
    void rejectsWhenOpsTokenIsNotConfigured() {
        ReflectionTestUtils.setField(interceptor, "opsToken", "");
        MockHttpServletRequest request = opsRequest();
        request.addHeader("X-Ops-Token", "anything");

        assertThatThrownBy(() -> interceptor.preHandle(request, response, new Object()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("OPS_TOKEN_NOT_CONFIGURED");
    }

    @Test
    void rejectsWhenHeaderIsMissing() {
        ReflectionTestUtils.setField(interceptor, "opsToken", OPS_TOKEN);

        assertThatThrownBy(() -> interceptor.preHandle(opsRequest(), response, new Object()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("OPS_TOKEN_INVALID");
    }

    @Test
    void rejectsWhenTokenDoesNotMatch() {
        ReflectionTestUtils.setField(interceptor, "opsToken", OPS_TOKEN);
        MockHttpServletRequest request = opsRequest();
        request.addHeader("X-Ops-Token", "wrong");

        assertThatThrownBy(() -> interceptor.preHandle(request, response, new Object()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("OPS_TOKEN_INVALID");
    }

    @Test
    void allowsWithMatchingTokenAndDoesNotTouchUserSession() {
        ReflectionTestUtils.setField(interceptor, "opsToken", OPS_TOKEN);
        MockHttpServletRequest request = opsRequest();
        request.addHeader("X-Ops-Token", OPS_TOKEN);

        assertThat(interceptor.preHandle(request, response, new Object())).isTrue();
        // 운영 토큰으로 통과했으면 유저 인증 경로를 타면 안 된다.
        verifyNoInteractions(authService);
    }

    @Test
    void opsPathIsNotTreatedAsPublic() {
        // 화이트리스트에 넣으면 무인증 공개가 된다. 토큰이 없으면 반드시 401 이어야 한다.
        ReflectionTestUtils.setField(interceptor, "opsToken", OPS_TOKEN);

        assertThatThrownBy(() -> interceptor.preHandle(opsRequest(), response, new Object()))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(exception -> ((ResponseStatusException) exception).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
