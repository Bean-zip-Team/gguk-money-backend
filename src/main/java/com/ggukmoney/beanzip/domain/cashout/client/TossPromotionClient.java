package com.ggukmoney.beanzip.domain.cashout.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

/**
 * 서버-투-서버 연동 스펙(https://developers-apps-in-toss.toss.im/bedrock/reference/framework/비게임/promotion.html).
 * mTLS 인증서가 아직 없어 이 클라이언트는 평범한 RestClient로만 구성돼 있다 — 인증서 확보 후 SSLContext 설정이 추가돼야
 * base-url을 실제로 호출할 수 있다.
 */
@Component
public class TossPromotionClient {

    private static final String GET_KEY_PATH =
            "/api-partner/v1/apps-in-toss/promotion/execute-promotion/get-key";
    private static final String EXECUTE_PROMOTION_PATH =
            "/api-partner/v1/apps-in-toss/promotion/execute-promotion";
    private static final String EXECUTION_RESULT_PATH =
            "/api-partner/v1/apps-in-toss/promotion/execution-result";

    private final RestClient restClient;
    private final String baseUrl;

    public TossPromotionClient(
            @Value("${app.cashout.toss.base-url:}") String baseUrl
    ) {
        this.baseUrl = baseUrl == null ? "" : baseUrl.trim();
        this.restClient = StringUtils.hasText(this.baseUrl)
                ? RestClient.builder().baseUrl(this.baseUrl).build()
                : RestClient.builder().build();
    }

    /**
     * 지급용 1회성 Key를 발급받는다. 이 호출 자체는 돈을 움직이지 않으므로, 실패 시(예외 종류와 무관하게)
     * 안전하게 재시도하거나 즉시 실패로 처리해도 된다.
     */
    public String getKey(String tossUserKey) {
        requireConfigured();
        try {
            TossPromotionKeyResponse response = restClient.post()
                    .uri(GET_KEY_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("x-toss-user-key", requireText(tossUserKey))
                    .retrieve()
                    .body(TossPromotionKeyResponse.class);

            if (response == null || response.success() == null || !StringUtils.hasText(response.success().key())) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "TOSS_SERVER_ERROR");
            }
            return response.success().key();
        } catch (RestClientResponseException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "TOSS_SERVER_ERROR", exception);
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "TOSS_SERVER_ERROR", exception);
        }
    }

    /**
     * 발급받은 Key로 실제 지급을 실행한다. 응답은 "접수 성공/실패"만 의미하고, 최종 지급 여부는
     * {@link #getExecutionResult(String)}로 별도 확인해야 한다.
     *
     * <p>Toss가 명시적으로 거절한 경우(4xx + 파싱 가능한 에러 바디)는 {@link PromotionExecutionOutcome#failed()}로
     * 반환한다 — 이 경우 Toss 쪽에서 아무 일도 일어나지 않았음이 확실하므로 호출자가 안전하게 환불 처리할 수 있다.
     * 반면 네트워크 오류·5xx·타임아웃처럼 Toss가 실제로 처리했는지 알 수 없는 경우는
     * {@link AmbiguousTossFailureException}을 던진다 — 호출자는 이 경우 자동 환불하면 안 된다.
     */
    public PromotionExecutionOutcome executePromotion(String promotionCode, String key, long amount) {
        requireConfigured();
        try {
            TossPromotionExecuteResponse response = restClient.post()
                    .uri(EXECUTE_PROMOTION_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new TossPromotionExecuteRequest(requireText(promotionCode), requireText(key), amount))
                    .retrieve()
                    .body(TossPromotionExecuteResponse.class);

            if (response == null) {
                throw new AmbiguousTossFailureException("execute-promotion 응답이 비어 있음");
            }
            if ("SUCCESS".equalsIgnoreCase(response.resultType())) {
                return PromotionExecutionOutcome.success();
            }
            return PromotionExecutionOutcome.failed(response.error() == null ? null : response.error().errorCode());
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().is4xxClientError()) {
                return PromotionExecutionOutcome.failed(extractErrorCode(exception));
            }
            throw new AmbiguousTossFailureException("execute-promotion 5xx 응답", exception);
        } catch (AmbiguousTossFailureException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new AmbiguousTossFailureException("execute-promotion 네트워크 오류", exception);
        }
    }

    /**
     * 지급 Key의 최종 처리 상태를 조회한다. {@code SUCCESS}/{@code PENDING}/{@code FAILED} 중 하나를 반환한다.
     */
    public PromotionResultStatus getExecutionResult(String key) {
        requireConfigured();
        try {
            TossPromotionResultResponse response = restClient.post()
                    .uri(EXECUTION_RESULT_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new TossPromotionResultRequest(requireText(key)))
                    .retrieve()
                    .body(TossPromotionResultResponse.class);

            if (response == null || !StringUtils.hasText(response.success())) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "TOSS_SERVER_ERROR");
            }
            return PromotionResultStatus.valueOf(response.success());
        } catch (RestClientResponseException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "TOSS_SERVER_ERROR", exception);
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "TOSS_SERVER_ERROR", exception);
        }
    }

    private String extractErrorCode(RestClientResponseException exception) {
        try {
            TossPromotionExecuteResponse body = exception.getResponseBodyAs(TossPromotionExecuteResponse.class);
            return body == null || body.error() == null ? null : body.error().errorCode();
        } catch (RuntimeException parseException) {
            return null;
        }
    }

    private void requireConfigured() {
        if (!StringUtils.hasText(baseUrl)) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "TOSS_CLIENT_NOT_CONFIGURED");
        }
    }

    private String requireText(String value) {
        if (!StringUtils.hasText(value)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "TOSS_REQUEST_INVALID");
        }
        return value.trim();
    }

    private record TossPromotionExecuteRequest(String promotionCode, String key, long amount) {
    }

    private record TossPromotionResultRequest(String key) {
    }

    public record TossPromotionKeyResponse(String resultType, TossPromotionKeySuccess success, TossPromotionError error) {
    }

    public record TossPromotionKeySuccess(String key) {
    }

    public record TossPromotionExecuteResponse(String resultType, Object success, TossPromotionError error) {
    }

    public record TossPromotionResultResponse(String resultType, String success, TossPromotionError error) {
    }

    public record TossPromotionError(String errorCode, String reason) {
    }

    public enum PromotionResultStatus {
        SUCCESS,
        PENDING,
        FAILED
    }

    public record PromotionExecutionOutcome(boolean succeeded, String tossErrorCode) {
        public static PromotionExecutionOutcome success() {
            return new PromotionExecutionOutcome(true, null);
        }

        public static PromotionExecutionOutcome failed(String tossErrorCode) {
            return new PromotionExecutionOutcome(false, tossErrorCode);
        }
    }

    public static class AmbiguousTossFailureException extends RuntimeException {
        public AmbiguousTossFailureException(String message) {
            super(message);
        }

        public AmbiguousTossFailureException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
