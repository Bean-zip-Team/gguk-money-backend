package com.ggukmoney.beanzip.global.config;

import org.springframework.boot.ssl.SslBundle;
import org.springframework.boot.ssl.SslBundles;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.util.StringUtils;

import java.net.http.HttpClient;
import java.time.Duration;

public final class TossClientHttpRequestFactory {

    private TossClientHttpRequestFactory() {
    }

    public static ClientHttpRequestFactory create(
            SslBundles sslBundles,
            String mtlsBundleName,
            Duration connectTimeout,
            Duration readTimeout
    ) {
        Duration validatedConnectTimeout = requirePositive(connectTimeout, "connectTimeout");
        Duration validatedReadTimeout = requirePositive(readTimeout, "readTimeout");
        SslBundle sslBundle = resolveBundle(sslBundles, mtlsBundleName);
        HttpClient httpClient = createHttpClient(sslBundle, validatedConnectTimeout);
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(validatedReadTimeout);
        return requestFactory;
    }

    static HttpClient createHttpClient(SslBundle sslBundle, Duration connectTimeout) {
        HttpClient.Builder builder = HttpClient.newBuilder()
                .connectTimeout(requirePositive(connectTimeout, "connectTimeout"));
        if (sslBundle != null) {
            builder.sslContext(sslBundle.createSslContext());
        }
        return builder.build();
    }

    private static SslBundle resolveBundle(SslBundles sslBundles, String mtlsBundleName) {
        if (sslBundles == null || !StringUtils.hasText(mtlsBundleName)) {
            return null;
        }
        String normalizedBundleName = mtlsBundleName.trim();
        if (!sslBundles.getBundleNames().contains(normalizedBundleName)) {
            return null;
        }
        return sslBundles.getBundle(normalizedBundleName);
    }

    private static Duration requirePositive(Duration value, String name) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return value;
    }
}
