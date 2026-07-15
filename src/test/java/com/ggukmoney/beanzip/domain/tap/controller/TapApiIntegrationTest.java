package com.ggukmoney.beanzip.domain.tap.controller;

import com.ggukmoney.beanzip.domain.keycap.entity.KeycapBoxAccount;
import com.ggukmoney.beanzip.domain.keycap.repository.KeycapBoxAccountRepository;
import com.ggukmoney.beanzip.domain.point.entity.PointAccount;
import com.ggukmoney.beanzip.domain.point.repository.PointAccountRepository;
import com.ggukmoney.beanzip.domain.tap.service.UserTapProgressService;
import com.ggukmoney.beanzip.domain.user.entity.AppUser;
import com.ggukmoney.beanzip.domain.user.repository.AppUserRepository;
import com.ggukmoney.beanzip.global.config.TapPolicyConfig;
import com.ggukmoney.beanzip.support.FullStackIntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TapApiIntegrationTest extends FullStackIntegrationTestSupport {

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private PointAccountRepository pointAccountRepository;

    @Autowired
    private KeycapBoxAccountRepository keycapBoxAccountRepository;

    @Autowired
    private UserTapProgressService userTapProgressService;

    @Autowired
    private TapPolicyConfig tapPolicyConfig;

    @Test
    void submitsBatchAndReturnsAcceptedCountAndBalanceWithoutExposingTarget() throws Exception {
        TestTokens tokens = registerUserWithSession("tester-1");
        UUID sessionId = UUID.randomUUID();

        mockMvc.perform(post("/api/tap/batches")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(batchJson(sessionId, 1, 50)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.acceptedCount").value(50))
                .andExpect(jsonPath("$.data.pointsAwarded").exists())
                .andExpect(jsonPath("$.data.boxesDropped").exists())
                .andExpect(jsonPath("$.data.balance").exists())
                .andExpect(jsonPath("$.data.nextPointTarget").doesNotExist())
                .andExpect(jsonPath("$.data.nextBoxTarget").doesNotExist());
    }

    @Test
    void duplicateSequenceDoesNotDoubleCreditBalance() throws Exception {
        TestTokens tokens = registerUserWithSession("tester-2");
        UUID sessionId = UUID.randomUUID();
        String body = batchJson(sessionId, 1, 50);

        mockMvc.perform(post("/api/tap/batches")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/tap/batches")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pointsAwarded").value(0));
    }

    @Test
    void rejectsOnceTokenBucketIsExhausted() throws Exception {
        TestTokens tokens = registerUserWithSession("tester-3");

        for (int i = 0; i < 8; i++) {
            mockMvc.perform(post("/api/tap/batches")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.accessToken())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(batchJson(UUID.randomUUID(), i, 1)))
                    .andExpect(status().isOk());
        }

        mockMvc.perform(post("/api/tap/batches")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(batchJson(UUID.randomUUID(), 99, 1)))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error.code").value("TAP_RATE_LIMITED"));
    }

    @Test
    void returnsZeroedStatusRightAfterRegistration() throws Exception {
        TestTokens tokens = registerUserWithSession("tester-4");

        mockMvc.perform(get("/api/tap/today")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.validTapCount").value(0))
                .andExpect(jsonPath("$.data.pointEarnedToday").value(0))
                .andExpect(jsonPath("$.data.remainingTapsToNextPoint").isNumber())
                .andExpect(jsonPath("$.data.remainingTapsToNextBox").isNumber());
    }

    @Test
    void reflectsAcceptedTapsAfterBatchSubmission() throws Exception {
        TestTokens tokens = registerUserWithSession("tester-5");

        mockMvc.perform(post("/api/tap/batches")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(batchJson(UUID.randomUUID(), 1, 50)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/tap/today")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.validTapCount").value(50));
    }

    private TestTokens registerUserWithSession(String nickname) {
        AppUser user = appUserRepository.save(AppUser.createActive(nickname, null));
        pointAccountRepository.save(PointAccount.createFor(user));
        keycapBoxAccountRepository.save(KeycapBoxAccount.createFor(user));
        userTapProgressService.createFor(user, tapPolicyConfig);
        return saveTokenBackedSession(user.getId(), UUID.randomUUID().toString());
    }

    private String batchJson(UUID tapSessionId, long sequence, int submittedCount) {
        return "{\"tapSessionId\":\"" + tapSessionId + "\",\"sequence\":" + sequence + ",\"submittedCount\":" + submittedCount + "}";
    }
}
