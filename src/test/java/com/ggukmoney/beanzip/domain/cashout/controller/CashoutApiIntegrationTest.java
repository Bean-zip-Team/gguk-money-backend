package com.ggukmoney.beanzip.domain.cashout.controller;

import com.ggukmoney.beanzip.domain.point.entity.PointAccount;
import com.ggukmoney.beanzip.domain.point.repository.PointAccountRepository;
import com.ggukmoney.beanzip.domain.point.service.PointAccountService;
import com.ggukmoney.beanzip.domain.user.entity.AppUser;
import com.ggukmoney.beanzip.domain.user.repository.AppUserRepository;
import com.ggukmoney.beanzip.support.FullStackIntegrationTestSupport;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

    @Test
    void submitsFullBalanceCashoutAndZeroesBalance() throws Exception {
        AppUser user = registerUser("cashout-tester-3");
        pointAccountService.credit(user.getId(), 134);
        TestTokens tokens = saveTokenBackedSession(user.getId(), UUID.randomUUID().toString());

        mockMvc.perform(post("/api/v1/cashouts")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.accessToken())
                        .header("Idempotency-Key", UUID.randomUUID().toString()))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.pointAmount").value(134))
                .andExpect(jsonPath("$.data.tossPointAmount").value(93))
                .andExpect(jsonPath("$.data.status").value("REQUESTED"));

        mockMvc.perform(get("/api/v1/cashouts/quote")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pointBalance").value(0));
    }

    @Test
    void rejectsSubmissionWithoutIdempotencyKeyHeader() throws Exception {
        AppUser user = registerUser("cashout-tester-4");
        pointAccountService.credit(user.getId(), 134);
        TestTokens tokens = saveTokenBackedSession(user.getId(), UUID.randomUUID().toString());

        mockMvc.perform(post("/api/v1/cashouts")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.accessToken()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsSubmissionBelowMinimumPoint() throws Exception {
        AppUser user = registerUser("cashout-tester-5");
        pointAccountService.credit(user.getId(), 9);
        TestTokens tokens = saveTokenBackedSession(user.getId(), UUID.randomUUID().toString());

        mockMvc.perform(post("/api/v1/cashouts")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.accessToken())
                        .header("Idempotency-Key", UUID.randomUUID().toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("CASHOUT_MINIMUM_NOT_MET"));
    }

    @Test
    void replaysSameCashoutIdForRepeatedIdempotencyKeyWithoutDoubleDebiting() throws Exception {
        AppUser user = registerUser("cashout-tester-6");
        pointAccountService.credit(user.getId(), 134);
        TestTokens tokens = saveTokenBackedSession(user.getId(), UUID.randomUUID().toString());
        String idempotencyKey = UUID.randomUUID().toString();

        String firstBody = mockMvc.perform(post("/api/v1/cashouts")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.accessToken())
                        .header("Idempotency-Key", idempotencyKey))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();
        String firstCashoutId = JsonPath.read(firstBody, "$.data.cashoutId");

        mockMvc.perform(post("/api/v1/cashouts")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.accessToken())
                        .header("Idempotency-Key", idempotencyKey))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.cashoutId").value(firstCashoutId))
                .andExpect(jsonPath("$.data.pointAmount").value(134))
                .andExpect(jsonPath("$.data.tossPointAmount").value(93))
                .andExpect(jsonPath("$.data.status").value("REQUESTED"));

        mockMvc.perform(get("/api/v1/cashouts/quote")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pointBalance").value(0));
    }

    private AppUser registerUser(String nickname) {
        AppUser user = appUserRepository.save(AppUser.createActive(nickname, null));
        pointAccountRepository.save(PointAccount.createFor(user));
        return user;
    }
}
