package com.ggukmoney.beanzip.domain.abuse.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 운영자 디스코드 채널로 알림을 보낸다. 채널 설정에서 만든 웹후크 URL 하나로 동작하고 봇 토큰은 필요 없다.
 *
 * <p>알림은 부가 기능이라 실패해도 예외를 던지지 않는다. 웹후크 URL 은 비밀값이라 로그에 남기지 않는다.
 */
@Component
public class DiscordWebhookClient {

    private static final Logger log = LoggerFactory.getLogger(DiscordWebhookClient.class);

    /** 디스코드가 메시지 한 건에 받는 최대 글자 수. */
    static final int CONTENT_LIMIT = 2_000;
    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    private final RestClient restClient;
    private final URI webhookUri;

    @Autowired
    public DiscordWebhookClient(@Value("${app.abuse.discord-webhook-url:}") String webhookUrl) {
        this(webhookUrl, RestClient.builder().requestFactory(timeoutRequestFactory()));
    }

    DiscordWebhookClient(String webhookUrl, RestClient.Builder builder) {
        this.webhookUri = parse(webhookUrl);
        this.restClient = builder.build();
    }

    /** 설정값이 잘못돼도 알림만 끄고 서버는 뜨게 한다. */
    private static URI parse(String webhookUrl) {
        if (!StringUtils.hasText(webhookUrl)) {
            return null;
        }
        try {
            URI uri = URI.create(webhookUrl.trim());
            // 파싱은 되지만 HTTP 클라이언트가 거부하는 값(스킴·호스트 누락)은 전송 때 URL 이 담긴 예외를 던진다.
            // 미리 걸러서 토큰이 로그에 남지 않게 한다.
            if (("https".equals(uri.getScheme()) || "http".equals(uri.getScheme())) && uri.getHost() != null) {
                return uri;
            }
        } catch (IllegalArgumentException ignored) {
            // 아래에서 같은 경고를 남긴다. 예외 메시지에 URL 이 들어 있어 그대로 찍지 않는다.
        }
        log.warn("DISCORD_WEBHOOK_DISABLED reason=INVALID_URL");
        return null;
    }

    public void send(String content) {
        if (!StringUtils.hasText(content)) {
            return;
        }
        if (webhookUri == null) {
            log.info("DISCORD_WEBHOOK_SKIPPED reason=NO_WEBHOOK_URL");
            return;
        }
        for (String chunk : split(content)) {
            try {
                restClient.post()
                        .uri(webhookUri)
                        .contentType(MediaType.APPLICATION_JSON)
                        // 문구에 @everyone 같은 글자가 섞여도 아무도 호출하지 않는다.
                        .body(Map.of("content", chunk, "allowed_mentions", Map.of("parse", List.of())))
                        .retrieve()
                        .toBodilessEntity();
            } catch (RuntimeException exception) {
                // ponytail: 재시도하지 않는다. 놓친 알림은 다음 시간 창에서 다시 보내지 않으므로 필요하면 로그로 확인한다.
                // 예외 메시지에는 URL 이 들어갈 수 있어 클래스 이름과 상태 코드만 남긴다(404 는 웹후크 삭제, 429 는 전송 제한).
                log.warn("DISCORD_WEBHOOK_FAILED reason={} status={}", exception.getClass().getSimpleName(),
                        exception instanceof RestClientResponseException response ? response.getStatusCode().value() : "-");
                return;
            }
        }
    }

    /** 줄 단위로 끊어 한 건이 글자 수 제한을 넘지 않게 나눈다. 한 줄이 제한보다 길면 그 줄만 자른다. */
    static List<String> split(String content) {
        List<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String line : content.split("\n")) {
            String fitted = line.length() > CONTENT_LIMIT ? line.substring(0, CONTENT_LIMIT) : line;
            if (!current.isEmpty() && current.length() + 1 + fitted.length() > CONTENT_LIMIT) {
                chunks.add(current.toString());
                current.setLength(0);
            }
            if (!current.isEmpty()) {
                current.append('\n');
            }
            current.append(fitted);
        }
        if (!current.isEmpty()) {
            chunks.add(current.toString());
        }
        return chunks;
    }

    private static JdkClientHttpRequestFactory timeoutRequestFactory() {
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(TIMEOUT).build());
        factory.setReadTimeout(TIMEOUT);
        return factory;
    }
}
