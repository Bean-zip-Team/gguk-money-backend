package com.ggukmoney.beanzip.domain.booster.controller;

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

class BoosterApiIntegrationTest extends FullStackIntegrationTestSupport {

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
    void activatesBoosterAndAppliesDoublePointsOnSubsequentTapBatch() throws Exception {
        TestTokens tokens = registerUserWithSession("booster-tester-1");

        mockMvc.perform(post("/api/v1/boosters/activate")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(activateJson(UUID.randomUUID())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.active").value(true))
                .andExpect(jsonPath("$.data.multiplier").value(2.0))
                .andExpect(jsonPath("$.data.remainingDailyCount").value(2));

        mockMvc.perform(post("/api/v1/tap/batches")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(batchJson(UUID.randomUUID(), 1, 350)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pointsAwarded").value(2));
    }

    @Test
    void rejectsActivationWhenAlreadyActive() throws Exception {
        TestTokens tokens = registerUserWithSession("booster-tester-2");

        mockMvc.perform(post("/api/v1/boosters/activate")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(activateJson(UUID.randomUUID())))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/boosters/activate")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(activateJson(UUID.randomUUID())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("BOOSTER_ALREADY_ACTIVE"));
    }

    @Test
    void rejectsActivationWithoutAdViewId() throws Exception {
        TestTokens tokens = registerUserWithSession("booster-tester-missing-ad-view");

        mockMvc.perform(post("/api/v1/boosters/activate")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("COMMON_VALIDATION_ERROR"));
    }

    @Test
    void currentReflectsActiveBoosterStateAfterActivation() throws Exception {
        TestTokens tokens = registerUserWithSession("booster-tester-3");

        mockMvc.perform(post("/api/v1/boosters/activate")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(activateJson(UUID.randomUUID())))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/boosters/current")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.active").value(true))
                .andExpect(jsonPath("$.data.multiplier").value(2.0))
                .andExpect(jsonPath("$.data.remainingSeconds").isNumber());
    }

    private TestTokens registerUserWithSession(String nickname) {
        AppUser user = appUserRepository.save(AppUser.createActive(nickname, null));
        pointAccountRepository.save(PointAccount.createFor(user));
        keycapBoxAccountRepository.save(KeycapBoxAccount.createFor(user));
        userTapProgressService.createFor(user, tapPolicyConfig);
        return saveTokenBackedSession(user.getId(), UUID.randomUUID().toString());
    }

    private String activateJson(UUID adViewId) {
        return "{\"adViewId\":\"" + adViewId + "\"}";
    }

    private String batchJson(UUID tapSessionId, long sequence, int submittedCount) {
        return "{\"tapSessionId\":\"" + tapSessionId + "\",\"sequence\":" + sequence + ",\"submittedCount\":" + submittedCount + "}";
    }
}
