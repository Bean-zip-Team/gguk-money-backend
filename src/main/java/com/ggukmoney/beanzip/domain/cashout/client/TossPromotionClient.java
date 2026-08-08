package com.ggukmoney.beanzip.domain.cashout.client;

import com.ggukmoney.beanzip.global.util.PayloadLoggingInterceptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ssl.SslBundle;
import org.springframework.boot.ssl.SslBundles;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

import java.net.http.HttpClient;

/**
 * 서버-투-서버 연동 스펙(https://developers-apps-in-toss.toss.im/bedrock/reference/framework/비게임/promotion.html).
 * mTLS 인증서가 아직 없어 이 클라이언트는 평범한 RestClient로만 구성돼 있다 — 인증서 확보 후 SSLContext 설정이 추가돼야
 * base-url을 실제로 호출할 수 있다.
 */
@Component
public class TossPromotionClient {

    private static final Logger log = LoggerFactory.getLogger(TossPromotionClient.class);

    private static final String GET_KEY_PATH =
            "/api-partner/v1/apps-in-toss/promotion/execute-promotion/get-key";
    private static final String EXECUTE_PROMOTION_PATH =
            "/api-partner/v1/apps-in-toss/promotion/execute-promotion";
    private static final String EXECUTION_RESULT_PATH =
            "/api-partner/v1/apps-in-toss/promotion/execution-result";
    private static final String MTLS_BUNDLE_NAME = "toss-promotion";

    private final RestClient restClient;
    private final String baseUrl;
    private final boolean mtlsEnabled;

    public TossPromotionClient(
            @Value("${app.cashout.toss.base-url:}") String baseUrl,
            SslBundles sslBundles
    ) {
        this.baseUrl = baseUrl == null ? "" : baseUrl.trim();
        RestClient.Builder builder = StringUtils.hasText(this.baseUrl)
                ? RestClient.builder().baseUrl(this.baseUrl)
                : RestClient.builder();
        this.mtlsEnabled = sslBundles.getBundleNames().contains(MTLS_BUNDLE_NAME);
        if (mtlsEnabled) {
            builder.requestFactory(mtlsRequestFactory(sslBundles.getBundle(MTLS_BUNDLE_NAME)));
        }
        builder.requestInterceptor(PayloadLoggingInterceptor.forLogger(log, "TossPromotion"));
        this.restClient = builder.build();
        log.info("TossPromotionClient initialized: baseUrl={} mtlsBundle={} mtlsEnabled={} availableBundles={}",
                this.baseUrl, MTLS_BUNDLE_NAME, mtlsEnabled, sslBundles.getBundleNames());
    }

    private static ClientHttpRequestFactory mtlsRequestFactory(SslBundle sslBundle) {
        HttpClient httpClient = HttpClient.newBuilder()
                .sslContext(sslBundle.createSslContext())
                .build();
        return new JdkClientHttpRequestFactory(httpClient);
    }

    /**
     * 지급용 1회성 Key를 발급받는다. 이 호출 자체는 돈을 움직이지 않으므로, 실패 시(예외 종류와 무관하게)
     * 안전하게 재시도하거나 즉시 실패로 처리해도 된다.
     */
    public String getKey(String tossUserKey) {
        requireConfigured();
        String headerName = "x-toss-user-key";
        String headerValue = requireText(tossUserKey);
        log.info("Toss get-key request: url={}{} header={} value={} mtlsEnabled={}",
                baseUrl, GET_KEY_PATH, headerName, mask(headerValue), mtlsEnabled);
        long callStart = System.currentTimeMillis();
        try {
            TossPromotionKeyResponse response = restClient.post()
                    .uri(GET_KEY_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(headerName, headerValue)
                    .retrieve()
                    .body(TossPromotionKeyResponse.class);

            log.info("Toss get-key response: resultType={} keyPresent={} error={} elapsedMs={}",
                    response == null ? null : response.resultType(),
                    response != null && response.success() != null && StringUtils.hasText(response.success().key()),
                    response == null ? null : response.error(),
                    System.currentTimeMillis() - callStart);

            if (response == null || response.success() == null || !StringUtils.hasText(response.success().key())) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "TOSS_SERVER_ERROR");
            }
            return response.success().key();
        } catch (RestClientResponseException exception) {
            log.warn("Toss get-key HTTP error: status={} body={} elapsedMs={}",
                    exception.getStatusCode(), exception.getResponseBodyAsString(), System.currentTimeMillis() - callStart, exception);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "TOSS_SERVER_ERROR", exception);
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            log.error("Toss get-key unexpected failure: elapsedMs={}", System.currentTimeMillis() - callStart, exception);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "TOSS_SERVER_ERROR", exception);
        }
    }

    /**
     * 발급받은 Key로 실제 지급을 실행한다. 응답은 "접수 성공/실패"만 의미하고, 최종 지급 여부는
     * {@link #getExecutionResult(String, String, String)}로 별도 확인해야 한다.
     *
     * <p>get-key와 마찬가지로 프로모션 대상을 식별하는 {@code x-toss-user-key} 헤더가 필수다(문서상
     * 세 API 모두 독립적으로 이 헤더 또는 {@code x-anon-key} 중 하나를 요구함).
     *
     * <p>Toss가 명시적으로 거절한 경우(4xx + 파싱 가능한 에러 바디)는 {@link PromotionExecutionOutcome#failed(String, String)}로
     * 반환한다 — 이 경우 Toss 쪽에서 아무 일도 일어나지 않았음이 확실하므로 호출자가 안전하게 환불 처리할 수 있다.
     * 반면 네트워크 오류·5xx·타임아웃처럼 Toss가 실제로 처리했는지 알 수 없는 경우는
     * {@link AmbiguousTossFailureException}을 던진다 — 호출자는 이 경우 자동 환불하면 안 된다.
     */
    public PromotionExecutionOutcome executePromotion(String tossUserKey, String promotionCode, String key, long amount) {
        requireConfigured();
        String headerValue = requireText(tossUserKey);
        String safePromotionCode = requireText(promotionCode);
        String safeKey = requireText(key);
        log.info("Toss execute-promotion request: url={}{} x-toss-user-key={} promotionCode={} key={} amount={} mtlsEnabled={}",
                baseUrl, EXECUTE_PROMOTION_PATH, mask(headerValue), safePromotionCode, mask(safeKey), amount, mtlsEnabled);
        long callStart = System.currentTimeMillis();
        try {
            TossPromotionExecuteResponse response = restClient.post()
                    .uri(EXECUTE_PROMOTION_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("x-toss-user-key", headerValue)
                    .body(new TossPromotionExecuteRequest(safePromotionCode, safeKey, amount))
                    .retrieve()
                    .body(TossPromotionExecuteResponse.class);

            log.info("Toss execute-promotion response: resultType={} error={} elapsedMs={}",
                    response == null ? null : response.resultType(),
                    response == null ? null : response.error(),
                    System.currentTimeMillis() - callStart);

            if (response == null) {
                throw new AmbiguousTossFailureException("execute-promotion 응답이 비어 있음");
            }
            if ("SUCCESS".equalsIgnoreCase(response.resultType())) {
                return PromotionExecutionOutcome.success();
            }
            return PromotionExecutionOutcome.failed(
                    response.error() == null ? null : response.error().errorCode(),
                    response.error() == null ? null : response.error().reason()
            );
        } catch (RestClientResponseException exception) {
            log.warn("Toss execute-promotion HTTP error: status={} body={} elapsedMs={}",
                    exception.getStatusCode(), exception.getResponseBodyAsString(), System.currentTimeMillis() - callStart);
            if (exception.getStatusCode().is4xxClientError()) {
                TossPromotionError error = extractError(exception);
                return PromotionExecutionOutcome.failed(
                        error == null ? null : error.errorCode(),
                        error == null ? null : error.reason()
                );
            }
            throw new AmbiguousTossFailureException("execute-promotion 5xx 응답", exception);
        } catch (AmbiguousTossFailureException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            log.error("Toss execute-promotion 네트워크 오류: elapsedMs={}", System.currentTimeMillis() - callStart, exception);
            throw new AmbiguousTossFailureException("execute-promotion 네트워크 오류", exception);
        }
    }

    /**
     * 지급 Key의 최종 처리 상태를 조회한다. {@code SUCCESS}/{@code PENDING}/{@code FAILED} 중 하나를 반환한다.
     * get-key/execute-promotion과 마찬가지로 {@code x-toss-user-key} 헤더가 필수고, 바디에도
     * execute-promotion 때와 동일한 {@code promotionCode}가 필수다(빠지면 errorCode=40000으로 거절됨).
     */
    public PromotionResultStatus getExecutionResult(String tossUserKey, String promotionCode, String key) {
        requireConfigured();
        long callStart = System.currentTimeMillis();
        try {
            TossPromotionResultResponse response = restClient.post()
                    .uri(EXECUTION_RESULT_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("x-toss-user-key", requireText(tossUserKey))
                    .body(new TossPromotionResultRequest(requireText(promotionCode), requireText(key)))
                    .retrieve()
                    .body(TossPromotionResultResponse.class);

            log.info("Toss execution-result response: success={} error={} elapsedMs={}",
                    response == null ? null : response.success(),
                    response == null ? null : response.error(),
                    System.currentTimeMillis() - callStart);

            if (response == null || !StringUtils.hasText(response.success())) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "TOSS_SERVER_ERROR");
            }
            return PromotionResultStatus.valueOf(response.success());
        } catch (RestClientResponseException exception) {
            log.warn("Toss execution-result HTTP error: status={} body={} elapsedMs={}",
                    exception.getStatusCode(), exception.getResponseBodyAsString(), System.currentTimeMillis() - callStart);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "TOSS_SERVER_ERROR", exception);
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            log.error("Toss execution-result unexpected failure: elapsedMs={}", System.currentTimeMillis() - callStart, exception);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "TOSS_SERVER_ERROR", exception);
        }
    }

    private TossPromotionError extractError(RestClientResponseException exception) {
        try {
            TossPromotionExecuteResponse body = exception.getResponseBodyAs(TossPromotionExecuteResponse.class);
            return body == null ? null : body.error();
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

    private String mask(String value) {
        if (value.length() <= 8) {
            return "***(len=" + value.length() + ")";
        }
        return value.substring(0, 4) + "***" + value.substring(value.length() - 4) + "(len=" + value.length() + ")";
    }

    private record TossPromotionExecuteRequest(String promotionCode, String key, long amount) {
    }

    private record TossPromotionResultRequest(String promotionCode, String key) {
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

    public record PromotionExecutionOutcome(boolean succeeded, String tossErrorCode, String reason) {
        public static PromotionExecutionOutcome success() {
            return new PromotionExecutionOutcome(true, null, null);
        }

        public static PromotionExecutionOutcome failed(String tossErrorCode, String reason) {
            return new PromotionExecutionOutcome(false, tossErrorCode, reason);
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
