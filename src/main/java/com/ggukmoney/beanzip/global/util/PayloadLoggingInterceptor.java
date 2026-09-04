package com.ggukmoney.beanzip.global.util;

import org.slf4j.Logger;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.util.StreamUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * 외부 서버-투-서버 연동 디버깅용 — RestClient에 붙이면 모든 요청/응답 전문(raw payload)을 로그로 남긴다.
 * 응답 바디는 한 번만 읽을 수 있는 스트림이라, 읽은 뒤 {@link BufferedClientHttpResponse}로 다시 감싸서
 * 이후 역직렬화가 정상 동작하게 한다.
 */
public final class PayloadLoggingInterceptor {

    private PayloadLoggingInterceptor() {
    }

    public static ClientHttpRequestInterceptor forLogger(Logger log, String clientName) {
        return (request, body, execution) -> {
            log.info("{} HTTP request payload: method={} uri={} headers={} body={}",
                    clientName, request.getMethod(), request.getURI(), request.getHeaders(),
                    new String(body, StandardCharsets.UTF_8));
            ClientHttpResponse response = execution.execute(request, body);
            byte[] responseBody = StreamUtils.copyToByteArray(response.getBody());
            log.info("{} HTTP response payload: status={} headers={} body={}",
                    clientName, response.getStatusCode(), response.getHeaders(),
                    new String(responseBody, StandardCharsets.UTF_8));
            return new BufferedClientHttpResponse(response, responseBody);
        };
    }

    private record BufferedClientHttpResponse(ClientHttpResponse delegate, byte[] body) implements ClientHttpResponse {

        @Override
        public HttpStatusCode getStatusCode() throws IOException {
            return delegate.getStatusCode();
        }

        @Override
        public String getStatusText() throws IOException {
            return delegate.getStatusText();
        }

        @Override
        public void close() {
            delegate.close();
        }

        @Override
        public InputStream getBody() {
            return new ByteArrayInputStream(body);
        }

        @Override
        public HttpHeaders getHeaders() {
            return delegate.getHeaders();
        }
    }
}
