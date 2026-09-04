package com.ggukmoney.beanzip.domain.auth.client;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.ggukmoney.beanzip.support.TossCryptoTestFixture;
import com.ggukmoney.beanzip.support.DelayedHttpServer;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ssl.SslBundles;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

import java.util.Base64;
import java.util.List;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class TossAuthClientTest {

    private static final String BASE_URL = "https://apps-in-toss-api.toss.im";
    private static final String LOGIN_ME_URL = BASE_URL + "/api-partner/v1/apps-in-toss/user/oauth2/login-me";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void generateTokenTimeoutUsesExistingServerFailureClassification() throws Exception {
        String successfulTokenResponse = """
                {"resultType":"SUCCESS","success":{"accessToken":"late-access-token","refreshToken":"late-refresh-token","tokenType":"Bearer","expiresIn":3600}}
                """;
        try (DelayedHttpServer server = DelayedHttpServer.start(Duration.ofSeconds(2), successfulTokenResponse)) {
            SslBundles sslBundles = mock(SslBundles.class);
            when(sslBundles.getBundleNames()).thenReturn(List.of());
            TossCryptoTestFixture.Context context = TossCryptoTestFixture.context();
            TossAuthClient client = new TossAuthClient(
                    objectMapper,
                    server.baseUrl(),
                    sslBundles,
                    new TossPersonalDataDecryptor(context.base64Key(), context.aad()),
                    Duration.ofSeconds(1),
                    Duration.ofMillis(150)
            );

            long startedAt = System.nanoTime();
            assertThatThrownBy(() -> client.generateToken("code", "DEFAULT"))
                    .isInstanceOf(ResponseStatusException.class)
                    .satisfies(exception -> {
                        ResponseStatusException response = (ResponseStatusException) exception;
                        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
                        assertThat(response.getReason()).isEqualTo("TOSS_SERVER_ERROR");
                    });
            assertThat(Duration.ofNanos(System.nanoTime() - startedAt))
                    .isLessThan(Duration.ofSeconds(1));
        }
    }

    @Test
    void decryptsOnlyNameAndPreservesUserKey() {
        String encryptedName = "encrypted-name";
        String encryptedEmail = "encrypted-email";
        TossPersonalDataDecryptor decryptor = mock(TossPersonalDataDecryptor.class);
        when(decryptor.decryptNullable(encryptedName)).thenReturn("김토스");
        ClientFixture fixture = clientFixture(decryptor);
        fixture.server().expect(requestTo(LOGIN_ME_URL))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer access-token"))
                .andRespond(withSuccess("""
                        {"resultType":"SUCCESS","success":{"userKey":1234567890123456789,"name":"encrypted-name","email":"encrypted-email"}}
                        """, MediaType.APPLICATION_JSON));

        TossAuthClient.TossLoginMe result = fixture.client().loginMe("access-token");

        assertThat(result.userKey()).isEqualTo("1234567890123456789");
        assertThat(result.nickname()).isEqualTo("김토스");
        assertThat(result.profileImageUrl()).isNull();
        verify(decryptor).decryptNullable(encryptedName);
        verifyNoMoreInteractions(decryptor);
        fixture.server().verify();
    }

    @Test
    void mapsAgreedTermsWithoutDecryptingOrLoggingThem() {
        String encryptedName = "encrypted-name";
        TossPersonalDataDecryptor decryptor = mock(TossPersonalDataDecryptor.class);
        when(decryptor.decryptNullable(encryptedName)).thenReturn("김토스");
        ClientFixture fixture = clientFixture(decryptor);
        fixture.server().expect(requestTo(LOGIN_ME_URL))
                .andRespond(withSuccess("""
                        {"resultType":"SUCCESS","success":{"userKey":123,"name":"encrypted-name","agreedTerms":["service_terms_v1","privacy_v2"]}}
                        """, MediaType.APPLICATION_JSON));

        TossAuthClient.TossLoginMe result = fixture.client().loginMe("access-token");

        assertThat(result.agreedTerms()).containsExactly("service_terms_v1", "privacy_v2");
        verify(decryptor).decryptNullable(encryptedName);
        fixture.server().verify();
    }

    @Test
    void decryptsLoginMeNameWithoutLoggingSensitiveValues() {
        TossCryptoTestFixture.Context context = TossCryptoTestFixture.context();
        String encryptedName = context.encrypt("민감한이름");
        String encryptedEmail = context.encrypt("private@example.com");
        TossPersonalDataDecryptor decryptor = new TossPersonalDataDecryptor(context.base64Key(), context.aad());
        ClientFixture fixture = clientFixture(decryptor);
        Logger logger = (Logger) LoggerFactory.getLogger(TossAuthClient.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        fixture.server().expect(requestTo(LOGIN_ME_URL))
                .andRespond(withSuccess("""
                        {"resultType":"SUCCESS","success":{"userKey":987654321,"name":"%s","email":"%s"}}
                        """.formatted(encryptedName, encryptedEmail), MediaType.APPLICATION_JSON));

        try {
            TossAuthClient.TossLoginMe result = fixture.client().loginMe("secret-access-token");

            assertThat(result.nickname()).isEqualTo("민감한이름");
            assertThat(appender.list)
                    .extracting(ILoggingEvent::getFormattedMessage)
                    .allSatisfy(message -> assertThat(message)
                            .doesNotContain(
                                    "민감한이름",
                                    "private@example.com",
                                    encryptedName,
                                    encryptedEmail,
                                    "secret-access-token",
                                    "987654321"
                            ));
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
        fixture.server().verify();
    }

    @Test
    void doesNotLogAuthorizationCodeOrIssuedTokens() {
        TossCryptoTestFixture.Context context = TossCryptoTestFixture.context();
        ClientFixture fixture = clientFixture(new TossPersonalDataDecryptor(context.base64Key(), context.aad()));
        Logger logger = (Logger) LoggerFactory.getLogger(TossAuthClient.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        fixture.server().expect(requestTo(BASE_URL + "/api-partner/v1/apps-in-toss/user/oauth2/generate-token"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("""
                        {"resultType":"SUCCESS","success":{"accessToken":"issued-access-token","refreshToken":"issued-refresh-token","tokenType":"Bearer","expiresIn":3600}}
                        """, MediaType.APPLICATION_JSON));

        try {
            TossAuthClient.TossToken token = fixture.client().generateToken("sensitive-authorization-code", "DEFAULT");

            assertThat(token.accessToken()).isEqualTo("issued-access-token");
            assertThat(appender.list)
                    .extracting(ILoggingEvent::getFormattedMessage)
                    .allSatisfy(message -> assertThat(message).doesNotContain(
                            "sensitive-authorization-code",
                            "issued-access-token",
                            "issued-refresh-token"
                    ));
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
        fixture.server().verify();
    }

    @Test
    void mapsInvalidCiphertextBase64ToBadGateway() {
        assertLoginMeFailure("not-base64!", TossPersonalDataDecryptionException.Failure.INVALID_CIPHERTEXT_BASE64,
                HttpStatus.BAD_GATEWAY, "TOSS_PERSONAL_DATA_INVALID_BASE64");
    }

    @Test
    void mapsInvalidCiphertextStructureToBadGateway() {
        String shortPayload = Base64.getEncoder().encodeToString(new byte[27]);
        assertLoginMeFailure(shortPayload, TossPersonalDataDecryptionException.Failure.INVALID_CIPHERTEXT,
                HttpStatus.BAD_GATEWAY, "TOSS_PERSONAL_DATA_INVALID_PAYLOAD");
    }

    @Test
    void mapsAuthenticationFailureToBadGateway() {
        TossCryptoTestFixture.Context context = TossCryptoTestFixture.context();
        byte[] tampered = Base64.getDecoder().decode(context.encrypt("김토스"));
        tampered[tampered.length - 1] ^= 1;
        assertLoginMeFailure(Base64.getEncoder().encodeToString(tampered),
                TossPersonalDataDecryptionException.Failure.AUTHENTICATION_FAILED,
                HttpStatus.BAD_GATEWAY, "TOSS_PERSONAL_DATA_AUTHENTICATION_FAILED");
    }

    @Test
    void mapsUnexpectedCryptoEngineFailureToInternalServerError() {
        TossPersonalDataDecryptor decryptor = mock(TossPersonalDataDecryptor.class);
        when(decryptor.decryptNullable("encrypted-name"))
                .thenThrow(new TossPersonalDataDecryptionException(
                        TossPersonalDataDecryptionException.Failure.DECRYPTION_FAILED
                ));
        ClientFixture fixture = clientFixture(decryptor);
        fixture.server().expect(requestTo(LOGIN_ME_URL))
                .andRespond(withSuccess("""
                        {"resultType":"SUCCESS","success":{"userKey":123,"name":"encrypted-name"}}
                        """, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> fixture.client().loginMe("access-token"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(exception -> {
                    ResponseStatusException response = (ResponseStatusException) exception;
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
                    assertThat(response.getReason()).isEqualTo("TOSS_PERSONAL_DATA_DECRYPTION_FAILED");
                });
    }

    private void assertLoginMeFailure(
            String encryptedName,
            TossPersonalDataDecryptionException.Failure expectedFailure,
            HttpStatus expectedStatus,
            String expectedReason
    ) {
        TossCryptoTestFixture.Context context = TossCryptoTestFixture.context();
        TossPersonalDataDecryptor decryptor = new TossPersonalDataDecryptor(context.base64Key(), context.aad());
        ClientFixture fixture = clientFixture(decryptor);
        fixture.server().expect(requestTo(LOGIN_ME_URL))
                .andRespond(withSuccess("""
                        {"resultType":"SUCCESS","success":{"userKey":123,"name":"%s"}}
                        """.formatted(encryptedName), MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> fixture.client().loginMe("access-token"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(exception -> {
                    ResponseStatusException response = (ResponseStatusException) exception;
                    assertThat(response.getStatusCode()).isEqualTo(expectedStatus);
                    assertThat(response.getReason()).isEqualTo(expectedReason);
                    assertThat(response.getCause())
                            .isInstanceOf(TossPersonalDataDecryptionException.class)
                            .extracting(cause -> ((TossPersonalDataDecryptionException) cause).failure())
                            .isEqualTo(expectedFailure);
                });
        fixture.server().verify();
    }

    private ClientFixture clientFixture(TossPersonalDataDecryptor decryptor) {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        TossAuthClient client = new TossAuthClient(objectMapper, BASE_URL, null, decryptor, builder);
        return new ClientFixture(client, server);
    }

    private record ClientFixture(TossAuthClient client, MockRestServiceServer server) {
    }
}
