package com.ggukmoney.beanzip.global.config;

import com.ggukmoney.beanzip.support.DelayedHttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.boot.ssl.SslBundle;
import org.springframework.boot.ssl.SslBundles;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.ResourceAccessException;

import javax.net.ssl.SSLContext;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TossClientHttpRequestFactoryTest {

    @Test
    void configuresConnectTimeoutWithoutMtls() {
        Duration connectTimeout = Duration.ofMillis(321);

        assertThat(TossClientHttpRequestFactory.createHttpClient(null, connectTimeout).connectTimeout())
                .contains(connectTimeout);
    }

    @Test
    void configuresSslContextWhenMtlsBundleExists() throws Exception {
        SslBundles sslBundles = mock(SslBundles.class);
        SslBundle sslBundle = mock(SslBundle.class);
        SSLContext sslContext = SSLContext.getDefault();
        when(sslBundles.getBundleNames()).thenReturn(List.of("toss-auth"));
        when(sslBundles.getBundle("toss-auth")).thenReturn(sslBundle);
        when(sslBundle.createSslContext()).thenReturn(sslContext);

        TossClientHttpRequestFactory.create(
                sslBundles,
                "toss-auth",
                Duration.ofSeconds(3),
                Duration.ofSeconds(10)
        );

        verify(sslBundle).createSslContext();
    }

    @Test
    void abortsDelayedResponseWithinReadTimeout() throws Exception {
        try (DelayedHttpServer server = DelayedHttpServer.start(Duration.ofSeconds(2), "{}")) {
            RestClient client = RestClient.builder()
                    .baseUrl(server.baseUrl())
                    .requestFactory(TossClientHttpRequestFactory.create(
                            null,
                            "toss-auth",
                            Duration.ofSeconds(1),
                            Duration.ofMillis(150)
                    ))
                    .build();

            long startedAt = System.nanoTime();

            assertThatThrownBy(() -> client.get().uri("/delayed").retrieve().toBodilessEntity())
                    .isInstanceOf(ResourceAccessException.class);
            assertThat(Duration.ofNanos(System.nanoTime() - startedAt))
                    .isLessThan(Duration.ofSeconds(1));
        }
    }
}
