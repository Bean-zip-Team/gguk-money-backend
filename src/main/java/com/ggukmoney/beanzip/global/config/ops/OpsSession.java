package com.ggukmoney.beanzip.global.config.ops;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.util.Arrays;
import java.util.Base64;

/**
 * 운영 화면(/ops/**) 로그인 쿠키.
 *
 * <ul>
 *   <li>토큰은 {@code app.ops.config-token} 이다. 조회용 {@code app.ops.token} 과 따로 둬서, 조회 토큰을 가진
 *       스크립트·사람이 정책(출금 환율 등)까지 바꾸지 못하게 한다. 비어 있으면 로그인할 수 없다(fail-closed).</li>
 *   <li>쿠키 값은 {@code 만료시각.서명} 이다. 서명은 토큰으로 만든 HMAC 이라 쿠키가 새도 토큰은 드러나지 않고,
 *       만료 시각이 서명에 들어가 12시간이 지나면 서버가 거부한다. 환경도 서명에 넣어 알파 쿠키는 운영에서 안 통한다.</li>
 *   <li>토큰을 바꾸면 모든 쿠키가 즉시 무효가 된다.</li>
 * </ul>
 */
@Component
public class OpsSession {

    public static final String COOKIE_NAME = "ops_session";
    private static final Duration MAX_AGE = Duration.ofHours(12);

    private final String token;
    private final String env;
    private final Clock clock;

    @Autowired
    public OpsSession(@Value("${app.ops.config-token:}") String token, Environment environment, Clock clock) {
        this(token, environment.matchesProfiles("alpha") ? "alpha" : "production", clock);
    }

    OpsSession(String token, String env, Clock clock) {
        this.token = token;
        this.env = env;
        this.clock = clock;
    }

    public boolean matchesToken(String presented) {
        return StringUtils.hasText(token)
                && StringUtils.hasText(presented)
                && MessageDigest.isEqual(
                        presented.getBytes(StandardCharsets.UTF_8),
                        token.getBytes(StandardCharsets.UTF_8));
    }

    public boolean isAuthenticated(HttpServletRequest request) {
        if (!StringUtils.hasText(token) || request.getCookies() == null) {
            return false;
        }
        return Arrays.stream(request.getCookies())
                .filter(cookie -> COOKIE_NAME.equals(cookie.getName()))
                .map(Cookie::getValue)
                .anyMatch(this::isValid);
    }

    public void start(HttpServletRequest request, HttpServletResponse response) {
        long expiresAt = clock.instant().plus(MAX_AGE).getEpochSecond();
        write(request, response, expiresAt + "." + sign(expiresAt), MAX_AGE);
    }

    public void end(HttpServletRequest request, HttpServletResponse response) {
        write(request, response, "", Duration.ZERO);
    }

    private boolean isValid(String value) {
        int dot = value == null ? -1 : value.indexOf('.');
        if (dot <= 0) {
            return false;
        }
        long expiresAt;
        try {
            expiresAt = Long.parseLong(value.substring(0, dot));
        } catch (NumberFormatException exception) {
            return false;
        }
        return expiresAt > clock.instant().getEpochSecond()
                && MessageDigest.isEqual(
                        value.substring(dot + 1).getBytes(StandardCharsets.UTF_8),
                        sign(expiresAt).getBytes(StandardCharsets.UTF_8));
    }

    private void write(HttpServletRequest request, HttpServletResponse response, String value, Duration maxAge) {
        // Lax: 슬랙 링크로 열어도 로그인이 유지된다. 쓰기 요청은 OpsPageInterceptor 가 Sec-Fetch-Site 로 막는다.
        ResponseCookie cookie = ResponseCookie.from(COOKIE_NAME, value)
                .path(request.getContextPath() + "/ops")
                .httpOnly(true)
                .secure(true)
                .sameSite("Lax")
                .maxAge(maxAge)
                .build();
        response.addHeader("Set-Cookie", cookie.toString());
    }

    private String sign(long expiresAt) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(token.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] signed = mac.doFinal(("ops-ui|" + env + "|" + expiresAt).getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(signed);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("HmacSHA256 unavailable", exception);
        }
    }
}
