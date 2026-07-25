package com.ggukmoney.beanzip.domain.notification.client;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class TossSmartMessageClientTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void sendsTossUserKeyAndTemplateSetCode() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        TossSmartMessageClient client = new TossSmartMessageClient(objectMapper, "https://apps-in-toss-api.toss.im", null, "toss-auth", builder);
        server.expect(requestTo("https://apps-in-toss-api.toss.im/api-partner/v1/apps-in-toss/messenger/send-message"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("x-toss-user-key", "toss-user-1"))
                .andExpect(jsonPath("$.templateSetCode").value("TPL_RANK"))
                .andRespond(withSuccess("""
                        {"resultType":"SUCCESS","success":{"contentId":"content-1"}}
                        """, MediaType.APPLICATION_JSON));

        TossSmartMessageClient.SendResult result = client.sendMessage("toss-user-1", "TPL_RANK");

        assertThat(result.succeeded()).isTrue();
        assertThat(result.contentId()).isEqualTo("content-1");
        assertThat(result.responseBody()).contains("content-1");
        server.verify();
    }

    @Test
    void failureResponseIsReturnedWithoutThrowing() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        TossSmartMessageClient client = new TossSmartMessageClient(objectMapper, "https://apps-in-toss-api.toss.im", null, "toss-auth", builder);
        server.expect(requestTo("https://apps-in-toss-api.toss.im/api-partner/v1/apps-in-toss/messenger/send-message"))
                .andRespond(withBadRequest().body("""
                        {"resultType":"FAIL","error":{"errorCode":"INVALID_TEMPLATE","reason":"invalid"}}
                        """).contentType(MediaType.APPLICATION_JSON));

        TossSmartMessageClient.SendResult result = client.sendMessage("toss-user-1", "TPL_RANK");

        assertThat(result.succeeded()).isFalse();
        assertThat(result.errorCode()).isEqualTo("INVALID_TEMPLATE");
        assertThat(result.retryable()).isFalse();
        assertThat(result.responseBody()).contains("INVALID_TEMPLATE");
    }

    @Test
    void channelResultsWithoutSuccessAreNotSent() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        TossSmartMessageClient client = new TossSmartMessageClient(objectMapper, "https://apps-in-toss-api.toss.im", null, "toss-auth", builder);
        server.expect(requestTo("https://apps-in-toss-api.toss.im/api-partner/v1/apps-in-toss/messenger/send-message"))
                .andRespond(withSuccess("""
                        {"resultType":"SUCCESS","success":{"contentId":"content-1","channelResults":[{"channel":"PUSH","success":false,"status":"FAILED"}]}}
                        """, MediaType.APPLICATION_JSON));

        TossSmartMessageClient.SendResult result = client.sendMessage("toss-user-1", "TPL_RANK");

        assertThat(result.succeeded()).isFalse();
        assertThat(result.errorCode()).isEqualTo("TOSS_SMART_MESSAGE_CHANNEL_FAILED");
        assertThat(result.retryable()).isTrue();
        assertThat(result.responseBody()).contains("channelResults");
    }

    @Test
    void serverErrorIsRetryable() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        TossSmartMessageClient client = new TossSmartMessageClient(objectMapper, "https://apps-in-toss-api.toss.im", null, "toss-auth", builder);
        server.expect(requestTo("https://apps-in-toss-api.toss.im/api-partner/v1/apps-in-toss/messenger/send-message"))
                .andRespond(withServerError().body("""
                        {"resultType":"FAIL","error":{"errorCode":"TEMPORARY","reason":"temporary"}}
                        """).contentType(MediaType.APPLICATION_JSON));

        TossSmartMessageClient.SendResult result = client.sendMessage("toss-user-1", "TPL_RANK");

        assertThat(result.succeeded()).isFalse();
        assertThat(result.retryable()).isTrue();
        assertThat(result.errorCode()).isEqualTo("TEMPORARY");
    }
}
