package com.ggukmoney.beanzip.domain.auth.client;

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
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.net.http.HttpClient;

@Component
public class TossAuthClient {

    private static final String GENERATE_TOKEN_PATH =
            "/api-partner/v1/apps-in-toss/user/oauth2/generate-token";
    private static final String LOGIN_ME_PATH =
            "/api-partner/v1/apps-in-toss/user/oauth2/login-me";
    private static final String REMOVE_BY_USER_KEY_PATH =
            "/api-partner/v1/apps-in-toss/user/oauth2/access/remove-by-user-key";
    private static final String MTLS_BUNDLE_NAME = "toss-auth";

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String baseUrl;

    public TossAuthClient(
            ObjectMapper objectMapper,
            @Value("${app.auth.toss.base-url:}") String baseUrl,
            SslBundles sslBundles
    ) {
        this.objectMapper = objectMapper;
        this.baseUrl = baseUrl == null ? "" : baseUrl.trim();
        RestClient.Builder builder = StringUtils.hasText(this.baseUrl)
                ? RestClient.builder().baseUrl(this.baseUrl)
                : RestClient.builder();
        if (sslBundles.getBundleNames().contains(MTLS_BUNDLE_NAME)) {
            builder.requestFactory(mtlsRequestFactory(sslBundles.getBundle(MTLS_BUNDLE_NAME)));
        }
        this.restClient = builder.build();
    }

    private static ClientHttpRequestFactory mtlsRequestFactory(SslBundle sslBundle) {
        HttpClient httpClient = HttpClient.newBuilder()
                .sslContext(sslBundle.createSslContext())
                .build();
        return new JdkClientHttpRequestFactory(httpClient);
    }

    public TossToken generateToken(String authorizationCode, String referrer) {
        requireConfigured();
        try {
            TossGenerateTokenResponse response = restClient.post()
                    .uri(GENERATE_TOKEN_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new TossGenerateTokenRequest(requireText(authorizationCode), referrer))
                    .retrieve()
                    .body(TossGenerateTokenResponse.class);

            if (response == null || response.success() == null || !StringUtils.hasText(response.success().accessToken())) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "TOSS_SERVER_ERROR");
            }
            return new TossToken(response.success().accessToken());
        } catch (RestClientResponseException exception) {
            throw convertException(exception);
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (RuntimeException exception) {
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

            if (response == null || response.success() == null || response.success().userKey() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "TOSS_USER_KEY_MISSING");
            }
            return new TossLoginMe(
                    String.valueOf(response.success().userKey()),
                    response.success().name(),
                    null
            );
        } catch (RestClientResponseException exception) {
            throw convertException(exception);
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (RuntimeException exception) {
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

    public record TossLoginMeSuccess(Long userKey, String name, String email) {
    }

    public record TossUnlinkResponse(String resultType, Object success, TossApiError error) {
    }

    public record TossApiError(String errorCode, String reason) {
    }

    private record TossGrantError(String error) {
    }

    public record TossToken(String accessToken) {
    }

    public record TossLoginMe(String userKey, String nickname, String profileImageUrl) {
    }
}
