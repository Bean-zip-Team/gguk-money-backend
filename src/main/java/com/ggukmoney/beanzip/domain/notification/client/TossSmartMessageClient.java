package com.ggukmoney.beanzip.domain.notification.client;

import com.ggukmoney.beanzip.global.config.TossClientHttpRequestFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ssl.SslBundles;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.JsonNode;

import java.time.Duration;
import java.util.List;

@Component
public class TossSmartMessageClient {

    private static final Logger log = LoggerFactory.getLogger(TossSmartMessageClient.class);

    private static final String SEND_MESSAGE_PATH = "/api-partner/v1/apps-in-toss/messenger/send-message";
    private static final String DEFAULT_BASE_URL = "https://apps-in-toss-api.toss.im";

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    @Autowired
    public TossSmartMessageClient(
            ObjectMapper objectMapper,
            @Value("${app.smart-message.toss.base-url:" + DEFAULT_BASE_URL + "}") String baseUrl,
            SslBundles sslBundles,
            @Value("${app.smart-message.toss.mtls-bundle-name:toss-auth}") String mtlsBundleName,
            @Value("${app.smart-message.toss.connect-timeout:3s}") Duration connectTimeout,
            @Value("${app.smart-message.toss.read-timeout:10s}") Duration readTimeout
    ) {
        this(
                objectMapper,
                baseUrl,
                sslBundles,
                mtlsBundleName,
                RestClient.builder().requestFactory(TossClientHttpRequestFactory.create(
                        sslBundles,
                        mtlsBundleName,
                        connectTimeout,
                        readTimeout
                ))
        );
        log.info("TossSmartMessageClient initialized: mtlsEnabled={} connectTimeout={} readTimeout={}",
                hasBundle(sslBundles, mtlsBundleName), connectTimeout, readTimeout);
    }

    TossSmartMessageClient(
            ObjectMapper objectMapper,
            String baseUrl,
            SslBundles sslBundles,
            String mtlsBundleName,
            RestClient.Builder builder
    ) {
        this.objectMapper = objectMapper;
        String normalizedBaseUrl = StringUtils.hasText(baseUrl) ? baseUrl.trim() : DEFAULT_BASE_URL;
        RestClient.Builder configuredBuilder = builder.baseUrl(normalizedBaseUrl);
        this.restClient = configuredBuilder.build();
    }

    public SendResult sendMessage(String tossUserKey, String templateSetCode) {
        return sendMessage(tossUserKey, templateSetCode, "{}");
    }

    public SendResult sendMessage(String tossUserKey, String templateSetCode, String contextJson) {
        try {
            TossSmartMessageResponse response = restClient.post()
                    .uri(SEND_MESSAGE_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("x-toss-user-key", requireText(tossUserKey))
                    .body(new TossSmartMessageRequest(requireText(templateSetCode), parseContext(contextJson)))
                    .retrieve()
                    .body(TossSmartMessageResponse.class);
            if (isSuccessResponse(response)) {
                if (hasChannelResults(response.success()) && !hasSuccessfulChannel(response.success())) {
                    return SendResult.failed(
                            "TOSS_SMART_MESSAGE_CHANNEL_FAILED",
                            "No successful smart message channel result",
                            true,
                            response.resultType(),
                            toResponseBody(response)
                    );
                }
                String contentId = response.success() == null ? null : response.success().contentId();
                return SendResult.succeeded(contentId, response.resultType(), toResponseBody(response));
            }
            if (response == null || response.success() == null || !StringUtils.hasText(response.success().contentId())) {
                TossSmartMessageError error = response == null ? null : response.error();
                return SendResult.failed(
                        error == null ? "TOSS_SMART_MESSAGE_FAILED" : error.errorCode(),
                        error == null ? null : error.reason(),
                        false,
                        response == null ? null : response.resultType(),
                        toResponseBody(response)
                );
            }
            if (!hasSuccessfulChannel(response.success())) {
                return SendResult.failed(
                        "TOSS_SMART_MESSAGE_CHANNEL_FAILED",
                        "No successful smart message channel result",
                        true,
                        response.resultType(),
                        toResponseBody(response)
                );
            }
            return SendResult.succeeded(response.success().contentId(), response.resultType(), toResponseBody(response));
        } catch (RestClientResponseException exception) {
            TossSmartMessageError error = extractError(exception.getResponseBodyAsString());
            return SendResult.failed(error == null ? "TOSS_SMART_MESSAGE_FAILED" : error.errorCode(),
                    error == null ? exception.getMessage() : error.reason(), isRetryableStatus(exception), null, exception.getResponseBodyAsString());
        } catch (RuntimeException exception) {
            return SendResult.failed("TOSS_SMART_MESSAGE_FAILED", exception.getMessage(), true, null, null);
        }
    }

    private boolean isSuccessResponse(TossSmartMessageResponse response) {
        return response != null && "SUCCESS".equalsIgnoreCase(response.resultType());
    }

    private static boolean hasBundle(SslBundles sslBundles, String bundleName) {
        return sslBundles != null
                && StringUtils.hasText(bundleName)
                && sslBundles.getBundleNames().contains(bundleName.trim());
    }

    private boolean isRetryableStatus(RestClientResponseException exception) {
        int statusCode = exception.getStatusCode().value();
        return statusCode == 408 || statusCode == 429 || statusCode >= 500;
    }

    private boolean hasSuccessfulChannel(TossSmartMessageSuccess success) {
        List<TossChannelResult> channelResults = success.channelResults();
        if (channelResults == null || channelResults.isEmpty()) {
            channelResults = success.results();
        }
        if (channelResults == null || channelResults.isEmpty()) {
            return StringUtils.hasText(success.contentId());
        }
        return channelResults.stream().anyMatch(TossChannelResult::isSuccessful);
    }

    private boolean hasChannelResults(TossSmartMessageSuccess success) {
        if (success == null) {
            return false;
        }
        return (success.channelResults() != null && !success.channelResults().isEmpty())
                || (success.results() != null && !success.results().isEmpty());
    }

    private String toResponseBody(TossSmartMessageResponse response) {
        if (response == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(response);
        } catch (JacksonException exception) {
            return null;
        }
    }

    private TossSmartMessageError extractError(String responseBody) {
        if (!StringUtils.hasText(responseBody)) {
            return null;
        }
        try {
            return objectMapper.readValue(responseBody, TossSmartMessageResponse.class).error();
        } catch (JacksonException exception) {
            return null;
        }
    }

    private String requireText(String value) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException("value is required");
        }
        return value.trim();
    }

    private JsonNode parseContext(String contextJson) {
        try {
            return objectMapper.readTree(StringUtils.hasText(contextJson) ? contextJson : "{}");
        } catch (JacksonException exception) {
            throw new IllegalArgumentException("invalid notification context", exception);
        }
    }

    private record TossSmartMessageRequest(String templateSetCode, JsonNode context) {
    }

    public record TossSmartMessageResponse(String resultType, TossSmartMessageSuccess success, TossSmartMessageError error) {
    }

    public record TossSmartMessageSuccess(String contentId, List<TossChannelResult> channelResults, List<TossChannelResult> results) {
    }

    public record TossChannelResult(String channel, Boolean success, String resultType, String status, String contentId, TossSmartMessageError error) {
        boolean isSuccessful() {
            return Boolean.TRUE.equals(success) || isSuccessText(resultType) || isSuccessText(status) || StringUtils.hasText(contentId);
        }

        private boolean isSuccessText(String value) {
            return "SUCCESS".equalsIgnoreCase(value) || "SENT".equalsIgnoreCase(value) || "SUCCEEDED".equalsIgnoreCase(value);
        }
    }

    public record TossSmartMessageError(String errorCode, String reason) {
    }

    public record SendResult(boolean succeeded, String contentId, String errorCode, String reason, boolean retryable,
                             String providerResultType, String responseBody) {
        static SendResult succeeded(String contentId, String providerResultType, String responseBody) {
            return new SendResult(true, contentId, null, null, false, providerResultType, responseBody);
        }

        static SendResult failed(String errorCode, String reason, boolean retryable, String providerResultType, String responseBody) {
            return new SendResult(false, null, errorCode, reason, retryable, providerResultType, responseBody);
        }
    }
}
