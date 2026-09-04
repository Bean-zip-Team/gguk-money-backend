package com.ggukmoney.beanzip.domain.auth.client;

import com.ggukmoney.beanzip.global.config.TossClientHttpRequestFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ssl.SslBundles;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.List;

@Component
public class TossAuthClient {

    private static final Logger log = LoggerFactory.getLogger(TossAuthClient.class);

    private static final String GENERATE_TOKEN_PATH =
            "/api-partner/v1/apps-in-toss/user/oauth2/generate-token";
    private static final String LOGIN_ME_PATH =
            "/api-partner/v1/apps-in-toss/user/oauth2/login-me";
    private static final String REMOVE_BY_USER_KEY_PATH =
            "/api-partner/v1/apps-in-toss/user/oauth2/access/remove-by-user-key";
    private static final String MTLS_BUNDLE_NAME = "toss-auth";

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final TossPersonalDataDecryptor personalDataDecryptor;
    private final String baseUrl;
    private final boolean mtlsEnabled;

    @Autowired
    public TossAuthClient(
            ObjectMapper objectMapper,
            @Value("${app.auth.toss.base-url:}") String baseUrl,
            SslBundles sslBundles,
            TossPersonalDataDecryptor personalDataDecryptor,
            @Value("${app.auth.toss.connect-timeout:3s}") Duration connectTimeout,
            @Value("${app.auth.toss.read-timeout:10s}") Duration readTimeout
    ) {
        this(
                objectMapper,
                baseUrl,
                sslBundles,
                personalDataDecryptor,
                RestClient.builder().requestFactory(TossClientHttpRequestFactory.create(
                        sslBundles,
                        MTLS_BUNDLE_NAME,
                        connectTimeout,
                        readTimeout
                ))
        );
        log.info("TossAuthClient timeouts configured: connectTimeout={} readTimeout={}",
                connectTimeout, readTimeout);
    }

    TossAuthClient(
            ObjectMapper objectMapper,
            String baseUrl,
            SslBundles sslBundles,
            TossPersonalDataDecryptor personalDataDecryptor,
            RestClient.Builder builder
    ) {
        this.objectMapper = objectMapper;
        this.personalDataDecryptor = personalDataDecryptor;
        this.baseUrl = baseUrl == null ? "" : baseUrl.trim();
        if (StringUtils.hasText(this.baseUrl)) {
            builder.baseUrl(this.baseUrl);
        }
        List<String> bundleNames = sslBundles == null ? List.of() : sslBundles.getBundleNames();
        this.mtlsEnabled = bundleNames.contains(MTLS_BUNDLE_NAME);
        this.restClient = builder.build();
        log.info("TossAuthClient initialized: baseUrlConfigured={} mtlsEnabled={}",
                StringUtils.hasText(this.baseUrl), mtlsEnabled);
    }

    public TossToken generateToken(String authorizationCode, String referrer) {
        requireConfigured();
        log.info("Toss generate-token request: path={} mtlsEnabled={}", GENERATE_TOKEN_PATH, mtlsEnabled);
        try {
            TossGenerateTokenResponse response = restClient.post()
                    .uri(GENERATE_TOKEN_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new TossGenerateTokenRequest(requireText(authorizationCode), referrer))
                    .retrieve()
                    .body(TossGenerateTokenResponse.class);

            log.info("Toss generate-token response: resultType={} accessTokenPresent={} tokenType={} expiresIn={} errorCode={}",
                    response == null ? null : response.resultType(),
                    response != null && response.success() != null && StringUtils.hasText(response.success().accessToken()),
                    response == null || response.success() == null ? null : response.success().tokenType(),
                    response == null || response.success() == null ? null : response.success().expiresIn(),
                    response == null || response.error() == null ? null : response.error().errorCode());

            if (response == null) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "TOSS_SERVER_ERROR");
            }
            if (response.success() == null || !StringUtils.hasText(response.success().accessToken())) {
                throw convertFailureResponse(response.error());
            }
            return new TossToken(response.success().accessToken());
        } catch (RestClientResponseException exception) {
            log.warn("Toss generate-token HTTP error: status={} errorCode={}",
                    exception.getStatusCode(), extractErrorCode(exception.getResponseBodyAsString()));
            throw convertException(exception);
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            log.error("Toss generate-token unexpected failure: exceptionType={}",
                    exception.getClass().getSimpleName());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "TOSS_SERVER_ERROR", exception);
        }
    }

    public TossLoginMe loginMe(String accessToken) {
        requireConfigured();
        try {
            TossLoginMeResponse response = restClient.get()
                    .uri(LOGIN_ME_PATH)
                    .header("Authorization", "Bearer " + requireText(accessToken))
                    .retrieve()
                    .body(TossLoginMeResponse.class);

            log.info("Toss login-me response: resultType={} successPresent={} userKeyPresent={} namePresent={} emailPresent={} errorCode={}",
                    response == null ? null : response.resultType(),
                    response != null && response.success() != null,
                    response != null && response.success() != null && response.success().userKey() != null,
                    response != null && response.success() != null && StringUtils.hasText(response.success().name()),
                    response != null && response.success() != null && StringUtils.hasText(response.success().email()),
                    response == null || response.error() == null ? null : response.error().errorCode());

            if (response == null || response.success() == null || response.success().userKey() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "TOSS_USER_KEY_MISSING");
            }
            String userKey = String.valueOf(response.success().userKey());
            return new TossLoginMe(
                    userKey,
                    personalDataDecryptor.decryptNullable(response.success().name()),
                    null,
                    response.success().agreedTerms() == null ? List.of() : List.copyOf(response.success().agreedTerms())
            );
        } catch (TossPersonalDataDecryptionException exception) {
            log.warn("Toss login-me personal data decryption failed: failure={}", exception.failure());
            throw convertDecryptionException(exception);
        } catch (RestClientResponseException exception) {
            log.warn("Toss login-me HTTP error: status={} errorCode={}",
                    exception.getStatusCode(), extractErrorCode(exception.getResponseBodyAsString()));
            throw convertException(exception);
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            log.error("Toss login-me unexpected failure: exceptionType={}",
                    exception.getClass().getSimpleName());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "TOSS_SERVER_ERROR", exception);
        }
    }

    public void removeByUserKey(String accessToken, String userKey) {
        requireConfigured();
        try {
            TossUnlinkResponse response = restClient.post()
                    .uri(REMOVE_BY_USER_KEY_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + requireText(accessToken))
                    .body(new TossUnlinkRequest(requireText(userKey)))
                    .retrieve()
                    .body(TossUnlinkResponse.class);

            if (response == null || response.success() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "TOSS_UNLINK_FAILED");
            }
        } catch (RestClientResponseException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "TOSS_UNLINK_FAILED", exception);
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "TOSS_UNLINK_FAILED", exception);
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

    private ResponseStatusException convertException(RestClientResponseException exception) {
        String errorCode = extractErrorCode(exception.getResponseBodyAsString());
        if ("invalid_grant".equalsIgnoreCase(errorCode)) {
            return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "TOSS_INVALID_GRANT", exception);
        }
        return new ResponseStatusException(HttpStatus.BAD_GATEWAY, "TOSS_SERVER_ERROR", exception);
    }

    private ResponseStatusException convertDecryptionException(TossPersonalDataDecryptionException exception) {
        return switch (exception.failure()) {
            case INVALID_CIPHERTEXT_BASE64 -> new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "TOSS_PERSONAL_DATA_INVALID_BASE64",
                    exception
            );
            case INVALID_CIPHERTEXT -> new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "TOSS_PERSONAL_DATA_INVALID_PAYLOAD",
                    exception
            );
            case AUTHENTICATION_FAILED -> new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "TOSS_PERSONAL_DATA_AUTHENTICATION_FAILED",
                    exception
            );
            case CONFIGURATION_MISSING, INVALID_KEY, DECRYPTION_FAILED -> new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "TOSS_PERSONAL_DATA_DECRYPTION_FAILED",
                    exception
            );
        };
    }

    private ResponseStatusException convertFailureResponse(TossApiError error) {
        if (error != null) {
            return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "TOSS_INVALID_GRANT");
        }
        return new ResponseStatusException(HttpStatus.BAD_GATEWAY, "TOSS_SERVER_ERROR");
    }

    private String extractErrorCode(String responseBody) {
        if (!StringUtils.hasText(responseBody)) {
            return null;
        }
        try {
            TossGrantError grantError = objectMapper.readValue(responseBody, TossGrantError.class);
            if (StringUtils.hasText(grantError.error())) {
                return grantError.error();
            }
            TossGenerateTokenResponse tokenResponse = objectMapper.readValue(responseBody, TossGenerateTokenResponse.class);
            return tokenResponse.error() == null ? null : tokenResponse.error().errorCode();
        } catch (JacksonException exception) {
            return null;
        }
    }

    private record TossGenerateTokenRequest(String authorizationCode, String referrer) {
    }

    private record TossUnlinkRequest(String userKey) {
    }

    public record TossGenerateTokenResponse(String resultType, TossGenerateTokenSuccess success, TossApiError error) {
    }

    public record TossGenerateTokenSuccess(String accessToken, String refreshToken, String tokenType, Long expiresIn) {
    }

    public record TossLoginMeResponse(String resultType, TossLoginMeSuccess success, TossApiError error) {
    }

    public record TossLoginMeSuccess(Long userKey, String name, String email, List<String> agreedTerms) {
    }

    public record TossUnlinkResponse(String resultType, Object success, TossApiError error) {
    }

    public record TossApiError(String errorCode, String reason) {
    }

    private record TossGrantError(String error) {
    }

    public record TossToken(String accessToken) {
    }

    public record TossLoginMe(String userKey, String nickname, String profileImageUrl, List<String> agreedTerms) {

        public TossLoginMe(String userKey, String nickname, String profileImageUrl) {
            this(userKey, nickname, profileImageUrl, List.of());
        }
    }
}
