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

            if (response == null) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "TOSS_SERVER_ERROR");
            }
            if (response.success() == null || !StringUtils.hasText(response.success().accessToken())) {
                throw convertFailureResponse(response.error());
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

    /**
     * Toss는 인가코드가 만료/재사용/불일치인 경우에도 HTTP 200에 {@code resultType: "FAIL"}로 응답한다
     * (4xx/5xx가 아니라 정상 HTTP 상태로 옴). {@code error}가 있다는 건 Toss가 요청을 정상적으로 받아
     * 처리하고 명확히 거부했다는 뜻이라 502(TOSS_SERVER_ERROR, "Toss와 통신 자체가 실패함")가 아니라
     * 401(TOSS_INVALID_GRANT, "인가코드가 유효하지 않음")로 매핑해야 한다.
     */
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
