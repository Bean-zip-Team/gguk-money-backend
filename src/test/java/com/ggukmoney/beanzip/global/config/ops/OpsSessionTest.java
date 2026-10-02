package com.ggukmoney.beanzip.global.config.ops;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class OpsSessionTest {

    private static final Instant NOW = Instant.parse("2026-10-03T00:00:00Z");

    @Test
    void acceptsItsOwnCookieUntilItExpires() {
        String cookie = issue(new OpsSession("token", "production", Clock.fixed(NOW, ZoneOffset.UTC)));

        assertThat(at("token", "production", NOW.plus(Duration.ofHours(11))).isAuthenticated(with(cookie))).isTrue();
        assertThat(at("token", "production", NOW.plus(Duration.ofHours(12))).isAuthenticated(with(cookie))).isFalse();
    }

    @Test
    void rejectsACookieFromAnotherTokenOrEnvironment() {
        String alphaCookie = issue(new OpsSession("token", "alpha", Clock.fixed(NOW, ZoneOffset.UTC)));

        assertThat(at("token", "production", NOW).isAuthenticated(with(alphaCookie))).isFalse();
        assertThat(at("rotated", "alpha", NOW).isAuthenticated(with(alphaCookie))).isFalse();
    }

    @Test
    void rejectsATamperedExpiry() {
        String cookie = issue(new OpsSession("token", "production", Clock.fixed(NOW, ZoneOffset.UTC)));
        String extended = "9999999999" + cookie.substring(cookie.indexOf('.'));

        assertThat(at("token", "production", NOW).isAuthenticated(with(extended))).isFalse();
        assertThat(at("token", "production", NOW).isAuthenticated(with("garbage"))).isFalse();
    }

    @Test
    void cannotLogInWhenNoTokenIsConfigured() {
        OpsSession unconfigured = at("", "production", NOW);

        assertThat(unconfigured.matchesToken("")).isFalse();
        assertThat(unconfigured.matchesToken("anything")).isFalse();
    }

    private static OpsSession at(String token, String env, Instant now) {
        return new OpsSession(token, env, Clock.fixed(now, ZoneOffset.UTC));
    }

    private static String issue(OpsSession session) {
        MockHttpServletResponse response = new MockHttpServletResponse();
        session.start(new MockHttpServletRequest(), response);
        return response.getCookie(OpsSession.COOKIE_NAME).getValue();
    }

    private static MockHttpServletRequest with(String cookieValue) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie(OpsSession.COOKIE_NAME, cookieValue));
        return request;
    }
}
