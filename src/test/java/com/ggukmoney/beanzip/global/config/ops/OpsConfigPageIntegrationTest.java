package com.ggukmoney.beanzip.global.config.ops;

import com.ggukmoney.beanzip.support.FullStackIntegrationTestSupport;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class OpsConfigPageIntegrationTest extends FullStackIntegrationTestSupport {

    private static final String CONFIG_TOKEN = "config-token-for-ops-page-test";
    private static final String READ_TOKEN = "read-only-ops-token-for-test";
    private static final String KEY = "tap.point.dailyCap";

    @DynamicPropertySource
    static void tokens(DynamicPropertyRegistry registry) {
        registry.add("app.ops.config-token", () -> CONFIG_TOKEN);
        registry.add("app.ops.token", () -> READ_TOKEN);
    }

    @Test
    void pagesRedirectToLoginWithoutASession() throws Exception {
        mockMvc.perform(get("/ops/config"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/ops/login"))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("X-Frame-Options", "DENY"));
    }

    @Test
    void neitherAWrongTokenNorTheReadOnlyOpsTokenStartsASession() throws Exception {
        mockMvc.perform(sameOrigin(post("/ops/login")).param("token", "wrong"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().doesNotExist("Set-Cookie"));
        mockMvc.perform(sameOrigin(post("/ops/login")).param("token", READ_TOKEN))
                .andExpect(status().isUnauthorized())
                .andExpect(header().doesNotExist("Set-Cookie"));
    }

    @Test
    void tokenInTheUrlIsRejected() throws Exception {
        mockMvc.perform(sameOrigin(post("/ops/login?token=" + CONFIG_TOKEN)))
                .andExpect(status().isUnauthorized())
                .andExpect(header().doesNotExist("Set-Cookie"));
    }

    @Test
    void sessionCookieIsHttpOnlySecureLaxAndIsNotTheRawToken() throws Exception {
        MvcResult login = mockMvc.perform(sameOrigin(post("/ops/login")).param("token", CONFIG_TOKEN))
                .andExpect(redirectedUrl("/ops/config"))
                .andReturn();

        assertThat(login.getResponse().getHeader("Set-Cookie"))
                .contains("HttpOnly")
                .contains("Secure")
                .contains("SameSite=Lax")
                .contains("Path=/ops")
                .doesNotContain(CONFIG_TOKEN);
    }

    @Test
    void listsCurrentValuesAndRecordsWhoChangedWhat() throws Exception {
        Cookie session = login();

        mockMvc.perform(get("/ops/config").cookie(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(KEY)))
                .andExpect(content().string(containsString("PRODUCTION")));

        mockMvc.perform(change(session, latestPublicId(), "200", "eun", "주말 이벤트"))
                .andExpect(redirectedUrl("/ops/config/" + KEY));

        Map<String, Object> latest = latestRow();
        assertThat(latest.get("config_value").toString()).isEqualTo("200");
        assertThat(latest.get("changed_by")).isEqualTo("eun");
        assertThat(latest.get("change_reason")).isEqualTo("주말 이벤트");
    }

    @Test
    void invalidValueMissingAuthorOrStaleFormIsNotSaved() throws Exception {
        Cookie session = login();
        String basedOn = latestPublicId();
        long before = rowCount();

        mockMvc.perform(change(session, basedOn, "abc", "eun", "test")).andExpect(status().isBadRequest());
        mockMvc.perform(change(session, basedOn, "300", " ", "test")).andExpect(status().isBadRequest());
        mockMvc.perform(change(session, basedOn, "300", "eun\nFAKE LOG LINE", "test")).andExpect(status().isBadRequest());
        mockMvc.perform(change(session, "00000000-0000-0000-0000-000000000000", "300", "eun", "test"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("다른 사람이 값을 바꿨습니다")));

        assertThat(rowCount()).isEqualTo(before);
    }

    @Test
    void writesFromAnotherOriginAreRejectedEvenWithASession() throws Exception {
        Cookie session = login();
        long before = rowCount();

        mockMvc.perform(post("/ops/config/" + KEY).cookie(session)
                        .header("Sec-Fetch-Site", "same-site")
                        .param("basedOn", latestPublicId()).param("value", "999")
                        .param("changedBy", "attacker").param("reason", "csrf"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/ops/config/" + KEY).cookie(session)
                        .param("basedOn", latestPublicId()).param("value", "999")
                        .param("changedBy", "attacker").param("reason", "no header"))
                .andExpect(status().isForbidden());

        assertThat(rowCount()).isEqualTo(before);
    }

    @Test
    void changeWithoutASessionIsRejected() throws Exception {
        long before = rowCount();

        mockMvc.perform(sameOrigin(post("/ops/config/" + KEY))
                        .param("basedOn", latestPublicId()).param("value", "999")
                        .param("changedBy", "attacker").param("reason", "no session"))
                .andExpect(redirectedUrl("/ops/login"));

        assertThat(rowCount()).isEqualTo(before);
    }

    @Test
    void apiRoutesStillRequireAJwt() throws Exception {
        mockMvc.perform(get("/api/tap/today")).andExpect(status().isUnauthorized());
    }

    private MockHttpServletRequestBuilder change(Cookie session, String basedOn, String value, String changedBy, String reason) {
        return sameOrigin(post("/ops/config/" + KEY)).cookie(session)
                .param("basedOn", basedOn)
                .param("value", value)
                .param("changedBy", changedBy)
                .param("reason", reason);
    }

    private static MockHttpServletRequestBuilder sameOrigin(MockHttpServletRequestBuilder request) {
        return request.header("Sec-Fetch-Site", "same-origin");
    }

    private Cookie login() throws Exception {
        MvcResult login = mockMvc.perform(sameOrigin(post("/ops/login")).param("token", CONFIG_TOKEN)).andReturn();
        return login.getResponse().getCookie(OpsSession.COOKIE_NAME);
    }

    private String latestPublicId() {
        return jdbcTemplate.queryForObject("""
                SELECT public_id::text FROM app_config WHERE config_key = ? ORDER BY effective_at DESC, id DESC LIMIT 1
                """, String.class, KEY);
    }

    private Map<String, Object> latestRow() {
        return jdbcTemplate.queryForMap("""
                SELECT config_value::text AS config_value, changed_by, change_reason
                FROM app_config WHERE config_key = ? ORDER BY effective_at DESC, id DESC LIMIT 1
                """, KEY);
    }

    private long rowCount() {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM app_config WHERE config_key = ?", Long.class, KEY);
    }
}
