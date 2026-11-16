package com.ggukmoney.beanzip.global.common;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 서비스가 던진 코드가 응답까지 그대로 나가는지 본다.
 *
 * <p>핸들러는 {@link ErrorCode} 에 없는 코드를 상태 코드로 대신 찾는데, 403·404·409·429 는 모두
 * {@code COMMON_INVALID_REQUEST}(400)로 떨어진다. 서비스 테스트는 예외 메시지만 보므로 등록을 빠뜨려도
 * 통과한다. 출금의 {@code ACCOUNT_SUSPENDED} 가 그렇게 400 으로 나가고 있었다.
 */
class ErrorCodeResponseTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @ParameterizedTest
    @CsvSource({
            "ACCOUNT_SUSPENDED, 403",
            "FRIENDS_DISABLED, 409",
            "INVITE_CODE_NOT_FOUND, 404",
            "INVITE_SELF, 400",
            "NOT_FRIENDS, 403",
            "CHEER_ALREADY_SENT_TODAY, 409",
            "CHEER_DAILY_LIMIT_EXCEEDED, 429",
    })
    void friendCodesReachTheResponseWithTheirOwnStatus(String code, int status) {
        ResponseEntity<ApiErrorResponse> response = handler.handleResponseStatusException(
                // 던지는 쪽이 적은 상태 코드가 아니라 등록된 상태 코드가 나간다.
                new ResponseStatusException(HttpStatus.I_AM_A_TEAPOT, code),
                new MockHttpServletRequest("POST", "/api/cheers")
        );

        assertThat(response.getStatusCode().value()).isEqualTo(status);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().success()).isFalse();
        assertThat(response.getBody().error().code()).isEqualTo(code);
        assertThat(response.getBody().error().message()).isNotBlank();
    }

    @Test
    void anUnregisteredCodeFallsBackToTheGenericBadRequest() {
        ResponseEntity<ApiErrorResponse> response = handler.handleResponseStatusException(
                new ResponseStatusException(HttpStatus.CONFLICT, "NOT_A_REGISTERED_CODE"),
                new MockHttpServletRequest("POST", "/api/cheers")
        );

        // 이 동작 때문에 새 코드는 반드시 ErrorCode 에 등록해야 한다.
        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody().error().code()).isEqualTo("COMMON_INVALID_REQUEST");
    }
}
