package com.ggukmoney.beanzip.domain.cashout.controller;

import com.ggukmoney.beanzip.domain.point.entity.PointAccount;
import com.ggukmoney.beanzip.domain.point.repository.PointAccountRepository;
import com.ggukmoney.beanzip.domain.point.service.PointAccountService;
import com.ggukmoney.beanzip.domain.user.entity.AppUser;
import com.ggukmoney.beanzip.domain.user.repository.AppUserRepository;
import com.ggukmoney.beanzip.support.FullStackIntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CashoutApiIntegrationTest extends FullStackIntegrationTestSupport {

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private PointAccountRepository pointAccountRepository;

    @Autowired
    private PointAccountService pointAccountService;

    @Test
    void returnsEligibleQuoteWhenBalanceMeetsMinimum() throws Exception {
        AppUser user = registerUser("cashout-tester-1");
        pointAccountService.credit(user.getId(), 134);

        TestTokens tokens = saveTokenBackedSession(user.getId(), UUID.randomUUID().toString());

        mockMvc.perform(get("/api/v1/cashouts/quote")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pointBalance").value(134))
                .andExpect(jsonPath("$.data.tossPointAmount").value(93))
                .andExpect(jsonPath("$.data.minimumPoint").value(10))
                .andExpect(jsonPath("$.data.rate.pointToKrw").value(0.7))
                .andExpect(jsonPath("$.data.eligible").value(true));
    }

    @Test
    void returnsIneligibleQuoteWhenBalanceBelowMinimum() throws Exception {
        AppUser user = registerUser("cashout-tester-2");
        pointAccountService.credit(user.getId(), 7);

        TestTokens tokens = saveTokenBackedSession(user.getId(), UUID.randomUUID().toString());

        mockMvc.perform(get("/api/v1/cashouts/quote")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tossPointAmount").value(4))
                .andExpect(jsonPath("$.data.eligible").value(false));
    }

    private AppUser registerUser(String nickname) {
        AppUser user = appUserRepository.save(AppUser.createActive(nickname, null));
        pointAccountRepository.save(PointAccount.createFor(user));
        return user;
    }
}
