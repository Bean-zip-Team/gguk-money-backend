package com.ggukmoney.beanzip.domain.point.controller;

import com.ggukmoney.beanzip.domain.point.entity.PointAccount;
import com.ggukmoney.beanzip.domain.point.repository.PointAccountRepository;
import com.ggukmoney.beanzip.domain.point.service.PointAccountService;
import com.ggukmoney.beanzip.domain.point.service.PointLedgerService;
import com.ggukmoney.beanzip.domain.user.entity.AppUser;
import com.ggukmoney.beanzip.domain.user.repository.AppUserRepository;
import com.ggukmoney.beanzip.support.FullStackIntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;

import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PointApiIntegrationTest extends FullStackIntegrationTestSupport {

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private PointAccountRepository pointAccountRepository;

    @Autowired
    private PointAccountService pointAccountService;

    @Autowired
    private PointLedgerService pointLedgerService;

    @Test
    void returnsBalanceAndCashoutEligibilityFields() throws Exception {
        AppUser user = registerUser("point-tester-1");
        creditPoints(user, 15);

        TestTokens tokens = saveTokenBackedSession(user.getId(), UUID.randomUUID().toString());

        mockMvc.perform(get("/api/v1/points/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.balance").value(15))
                .andExpect(jsonPath("$.data.cashoutEligible").value(true))
                .andExpect(jsonPath("$.data.minimumPoint").value(10))
                .andExpect(jsonPath("$.data.estimatedKrw").value(10));
    }

    @Test
    void paginatesLedgerWithCursorAcrossTwoPages() throws Exception {
        AppUser user = registerUser("point-tester-2");
        for (int i = 0; i < 3; i++) {
            creditPoints(user, 1);
        }

        TestTokens tokens = saveTokenBackedSession(user.getId(), UUID.randomUUID().toString());

        String firstPageBody = mockMvc.perform(get("/api/v1/points/ledger")
                        .param("size", "2")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(2))
                .andExpect(jsonPath("$.data.hasMore").value(true))
                .andExpect(jsonPath("$.data.nextCursor").isNotEmpty())
                .andReturn().getResponse().getContentAsString();

        String cursor = extractNextCursor(firstPageBody);

        mockMvc.perform(get("/api/v1/points/ledger")
                        .param("size", "2")
                        .param("cursor", cursor)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.hasMore").value(false))
                .andExpect(jsonPath("$.data.nextCursor").doesNotExist());
    }

    private AppUser registerUser(String nickname) {
        AppUser user = appUserRepository.save(AppUser.createActive(nickname, null));
        pointAccountRepository.save(PointAccount.createFor(user));
        return user;
    }

    private void creditPoints(AppUser user, long amount) {
        PointAccount account = pointAccountService.credit(user.getId(), amount);
        pointLedgerService.recordCredit(account, user, amount, "TAP_REWARD", UUID.randomUUID());
    }

    private String extractNextCursor(String responseBody) {
        Matcher matcher = Pattern.compile("\"nextCursor\":\"([^\"]+)\"").matcher(responseBody);
        if (!matcher.find()) {
            throw new IllegalStateException("nextCursor not found in response: " + responseBody);
        }
        return matcher.group(1);
    }
}
