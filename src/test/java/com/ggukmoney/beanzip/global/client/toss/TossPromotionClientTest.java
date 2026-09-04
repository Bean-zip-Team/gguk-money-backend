package com.ggukmoney.beanzip.global.client.toss;

import com.ggukmoney.beanzip.support.DelayedHttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.boot.ssl.SslBundles;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TossPromotionClientTest {

    @Test
    void getKeyTimeoutUsesExistingServerFailureClassification() throws Exception {
        try (DelayedHttpServer server = DelayedHttpServer.start(Duration.ofSeconds(2), "{}")) {
            TossPromotionClient client = client(server);

            assertThatThrownBy(() -> client.getKey("toss-user-key"))
                    .isInstanceOf(ResponseStatusException.class)
                    .satisfies(exception -> {
                        ResponseStatusException response = (ResponseStatusException) exception;
                        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
                        assertThat(response.getReason()).isEqualTo("TOSS_SERVER_ERROR");
                    });
        }
    }

    @Test
    void executePromotionTimeoutRemainsAmbiguous() throws Exception {
        try (DelayedHttpServer server = DelayedHttpServer.start(Duration.ofSeconds(2), "{}")) {
            TossPromotionClient client = client(server);

            assertThatThrownBy(() -> client.executePromotion("toss-user-key", "PROMOTION", "key", 100L))
                    .isInstanceOf(TossPromotionClient.AmbiguousTossFailureException.class);
        }
    }

    @Test
    void executionResultTimeoutUsesExistingServerFailureClassification() throws Exception {
        try (DelayedHttpServer server = DelayedHttpServer.start(Duration.ofSeconds(2), "{}")) {
            TossPromotionClient client = client(server);

            assertThatThrownBy(() -> client.getExecutionResult("toss-user-key", "PROMOTION", "key"))
                    .isInstanceOf(ResponseStatusException.class)
                    .satisfies(exception -> {
                        ResponseStatusException response = (ResponseStatusException) exception;
                        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
                        assertThat(response.getReason()).isEqualTo("TOSS_SERVER_ERROR");
                    });
        }
    }

    private TossPromotionClient client(DelayedHttpServer server) {
        SslBundles sslBundles = mock(SslBundles.class);
        when(sslBundles.getBundleNames()).thenReturn(List.of());
        return new TossPromotionClient(
                server.baseUrl(),
                sslBundles,
                Duration.ofSeconds(1),
                Duration.ofMillis(150)
        );
    }
}
