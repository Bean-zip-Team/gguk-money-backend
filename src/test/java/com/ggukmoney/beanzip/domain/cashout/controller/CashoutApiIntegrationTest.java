package com.ggukmoney.beanzip.domain.cashout.controller;

import com.ggukmoney.beanzip.domain.auth.entity.AuthIdentity;
import com.ggukmoney.beanzip.domain.auth.repository.AuthIdentityRepository;
import com.ggukmoney.beanzip.domain.cashout.client.TossPromotionClient;
import com.ggukmoney.beanzip.domain.cashout.entity.CashoutRequest;
import com.ggukmoney.beanzip.domain.cashout.repository.CashoutRequestRepository;
import com.ggukmoney.beanzip.domain.point.entity.PointAccount;
import com.ggukmoney.beanzip.domain.point.repository.PointAccountRepository;
import com.ggukmoney.beanzip.domain.point.service.PointAccountService;
import com.ggukmoney.beanzip.domain.user.entity.AppUser;
import com.ggukmoney.beanzip.domain.user.repository.AppUserRepository;
import com.ggukmoney.beanzip.support.FullStackIntegrationTestSupport;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;
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

    @Autowired
    private CashoutRequestRepository cashoutRequestRepository;

    @Autowired
    private AuthIdentityRepository authIdentityRepository;

    @MockitoBean
    private TossPromotionClient tossPromotionClient;

    @BeforeEach
    void stubTossPromotionSuccessByDefault() {
        reset(tossPromotionClient);
        when(tossPromotionClient.getKey(anyString())).thenReturn("promo-key");
        when(tossPromotionClient.executePromotion(anyString(), anyString(), anyLong()))
                .thenReturn(TossPromotionClient.PromotionExecutionOutcome.success());
    }

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
                .andExpect(jsonPath("$.data.status").value("PROCESSING"));

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
                .andExpect(jsonPath("$.data.status").value("PROCESSING"));

        mockMvc.perform(get("/api/v1/cashouts/quote")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pointBalance").value(0));
    }

    @Test
    void listsCashoutHistoryWithCursorPagination() throws Exception {
        AppUser user = registerUser("cashout-tester-7");
        TestTokens tokens = saveTokenBackedSession(user.getId(), UUID.randomUUID().toString());
        for (int i = 0; i < 3; i++) {
            cashoutRequestRepository.save(CashoutRequest.createFor(user, 10, 7, UUID.randomUUID()));
        }

        String firstPageBody = mockMvc.perform(get("/api/v1/cashouts")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.accessToken())
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(2))
                .andExpect(jsonPath("$.data.hasMore").value(true))
                .andReturn().getResponse().getContentAsString();
        String nextCursor = JsonPath.read(firstPageBody, "$.data.nextCursor");

        mockMvc.perform(get("/api/v1/cashouts")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.accessToken())
                        .param("size", "2")
                        .param("cursor", nextCursor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.hasMore").value(false));
    }

    @Test
    void filtersCashoutHistoryByStatus() throws Exception {
        AppUser user = registerUser("cashout-tester-8");
        TestTokens tokens = saveTokenBackedSession(user.getId(), UUID.randomUUID().toString());
        cashoutRequestRepository.save(CashoutRequest.createFor(user, 10, 7, UUID.randomUUID()));
        CashoutRequest succeeded = cashoutRequestRepository.save(CashoutRequest.createFor(user, 20, 14, UUID.randomUUID()));
        ReflectionTestUtils.setField(succeeded, "status", CashoutRequest.Status.SUCCEEDED);
        cashoutRequestRepository.save(succeeded);

        mockMvc.perform(get("/api/v1/cashouts")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.accessToken())
                        .param("status", "SUCCEEDED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.data.items[0].completedAt").exists());
    }

    @Test
    void returnsCashoutDetailForOwner() throws Exception {
        AppUser user = registerUser("cashout-tester-9");
        pointAccountService.credit(user.getId(), 134);
        TestTokens tokens = saveTokenBackedSession(user.getId(), UUID.randomUUID().toString());

        String submitBody = mockMvc.perform(post("/api/v1/cashouts")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.accessToken())
                        .header("Idempotency-Key", UUID.randomUUID().toString()))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();
        String cashoutId = JsonPath.read(submitBody, "$.data.cashoutId");

        mockMvc.perform(get("/api/v1/cashouts/" + cashoutId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.cashoutId").value(cashoutId))
                .andExpect(jsonPath("$.data.pointAmount").value(134))
                .andExpect(jsonPath("$.data.tossPointAmount").value(93))
                .andExpect(jsonPath("$.data.status").value("PROCESSING"))
                .andExpect(jsonPath("$.data.completedAt").doesNotExist());
    }

    @Test
    void returnsNotFoundForNonExistentCashoutId() throws Exception {
        AppUser user = registerUser("cashout-tester-10");
        TestTokens tokens = saveTokenBackedSession(user.getId(), UUID.randomUUID().toString());

        mockMvc.perform(get("/api/v1/cashouts/" + UUID.randomUUID())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.accessToken()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("CASHOUT_NOT_FOUND"));
    }

    @Test
    void returnsNotFoundWhenCashoutIdBelongsToAnotherUser() throws Exception {
        AppUser owner = registerUser("cashout-tester-11");
        pointAccountService.credit(owner.getId(), 134);
        TestTokens ownerTokens = saveTokenBackedSession(owner.getId(), UUID.randomUUID().toString());
        String submitBody = mockMvc.perform(post("/api/v1/cashouts")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerTokens.accessToken())
                        .header("Idempotency-Key", UUID.randomUUID().toString()))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();
        String cashoutId = JsonPath.read(submitBody, "$.data.cashoutId");

        AppUser stranger = registerUser("cashout-tester-12");
        TestTokens strangerTokens = saveTokenBackedSession(stranger.getId(), UUID.randomUUID().toString());

        mockMvc.perform(get("/api/v1/cashouts/" + cashoutId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + strangerTokens.accessToken()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("CASHOUT_NOT_FOUND"));
    }

    @Test
    void refundsBalanceWhenTossExplicitlyRejectsPromotionExecution() throws Exception {
        AppUser user = registerUser("cashout-tester-13");
        pointAccountService.credit(user.getId(), 134);
        TestTokens tokens = saveTokenBackedSession(user.getId(), UUID.randomUUID().toString());

        when(tossPromotionClient.executePromotion(anyString(), anyString(), anyLong()))
                .thenReturn(TossPromotionClient.PromotionExecutionOutcome.failed("4112"));

        mockMvc.perform(post("/api/v1/cashouts")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.accessToken())
                        .header("Idempotency-Key", UUID.randomUUID().toString()))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.status").value("FAILED"));

        mockMvc.perform(get("/api/v1/cashouts/quote")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pointBalance").value(134));
    }

    private AppUser registerUser(String nickname) {
        AppUser user = appUserRepository.save(AppUser.createActive(nickname, null));
        pointAccountRepository.save(PointAccount.createFor(user));
        authIdentityRepository.save(AuthIdentity.toss(user, "toss-user-key-" + nickname));
        return user;
    }
}
