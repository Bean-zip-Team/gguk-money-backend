package com.ggukmoney.beanzip.domain.abuse.client;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.springframework.test.web.client.ExpectedCount.never;
import static org.springframework.test.web.client.ExpectedCount.times;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.anything;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withNoContent;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;

class DiscordWebhookClientTest {

    private static final String WEBHOOK = "https://discord.com/api/webhooks/1/token";

    @Test
    void postsContentWithMentionsDisabled() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(WEBHOOK))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.content").value("탐지 결과"))
                .andExpect(jsonPath("$.allowed_mentions.parse").isEmpty())
                .andRespond(withNoContent());

        new DiscordWebhookClient(WEBHOOK, builder).send("탐지 결과");

        server.verify();
    }

    @Test
    void skipsWithoutWebhookUrlOrContent() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(never(), anything());

        new DiscordWebhookClient("", builder).send("탐지 결과");
        new DiscordWebhookClient("not a uri", builder).send("탐지 결과");
        // 파싱은 되지만 전송 때 HTTP 클라이언트가 URL 을 담아 예외를 던지는 값들이다.
        new DiscordWebhookClient("https:/discord.com/api/webhooks/1/x", builder).send("탐지 결과");
        new DiscordWebhookClient("discord.com/api/webhooks/1/x", builder).send("탐지 결과");
        new DiscordWebhookClient("ftp://discord.com/api/webhooks/1/x", builder).send("탐지 결과");
        new DiscordWebhookClient(WEBHOOK, builder).send("");

        server.verify();
    }

    @Test
    void failureDoesNotThrow() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(times(1), requestTo(WEBHOOK)).andRespond(withServerError());

        assertThatCode(() -> new DiscordWebhookClient(WEBHOOK, builder).send("탐지 결과"))
                .doesNotThrowAnyException();
        server.verify();
    }

    @Test
    void splitsLongContentOnLineBoundaries() {
        String line = "가".repeat(900);
        String overLimit = "나".repeat(DiscordWebhookClient.CONTENT_LIMIT + 10);

        assertThat(DiscordWebhookClient.split(line + "\n" + line + "\n" + line))
                .containsExactly(line + "\n" + line, line);
        assertThat(DiscordWebhookClient.split(overLimit))
                .singleElement()
                .satisfies(chunk -> assertThat(chunk).hasSize(DiscordWebhookClient.CONTENT_LIMIT));
    }
}
