package com.ggukmoney.beanzip.domain.friend.controller;

import com.ggukmoney.beanzip.domain.auth.service.AuthService;
import com.ggukmoney.beanzip.domain.friend.dto.response.CheerSendResponse;
import com.ggukmoney.beanzip.domain.friend.service.CheerService;
import com.ggukmoney.beanzip.domain.friend.service.FriendAccess;
import com.ggukmoney.beanzip.global.common.GlobalExceptionHandler;
import com.ggukmoney.beanzip.global.config.FriendPolicyConfig;
import com.ggukmoney.beanzip.global.interceptor.AuthInterceptor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

import static com.ggukmoney.beanzip.domain.friend.controller.FriendControllerTestSupport.TOKEN;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CheerControllerTest {

    private static final UUID FRIEND = UUID.fromString("33333333-3333-3333-3333-333333333333");

    private final AuthService authService = mock(AuthService.class);
    private final FriendPolicyConfig friendPolicy = mock(FriendPolicyConfig.class);
    private final CheerService cheerService = mock(CheerService.class);
    private final MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new CheerController(new FriendAccess(friendPolicy), cheerService))
            .addInterceptors(new AuthInterceptor(authService))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

    @Test
    void sendsACheerAndReportsWhatTheSenderGot() throws Exception {
        UUID userId = FriendControllerTestSupport.authenticate(authService);
        UUID cheerId = UUID.randomUUID();
        when(friendPolicy.friendsEnabled()).thenReturn(true);
        when(cheerService.send(userId, FRIEND)).thenReturn(new CheerSendResponse(cheerId, true, 1, 6));

        mockMvc.perform(sendCheer("{\"toUserId\":\"" + FRIEND + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.cheerId").value(cheerId.toString()))
                .andExpect(jsonPath("$.data.firstOfDay").value(true))
                .andExpect(jsonPath("$.data.shardsGranted").value(1))
                .andExpect(jsonPath("$.data.cheersLeft").value(6));
    }

    @ParameterizedTest
    @CsvSource({
            "FORBIDDEN, NOT_FRIENDS, 403",
            "FORBIDDEN, ACCOUNT_SUSPENDED, 403",
            "CONFLICT, CHEER_ALREADY_SENT_TODAY, 409",
            "TOO_MANY_REQUESTS, CHEER_DAILY_LIMIT_EXCEEDED, 429",
    })
    void aRefusedCheerKeepsItsOwnCodeAndStatus(HttpStatus thrown, String code, int expectedStatus) throws Exception {
        UUID userId = FriendControllerTestSupport.authenticate(authService);
        when(friendPolicy.friendsEnabled()).thenReturn(true);
        when(cheerService.send(userId, FRIEND)).thenThrow(new ResponseStatusException(thrown, code));

        mockMvc.perform(sendCheer("{\"toUserId\":\"" + FRIEND + "\"}"))
                .andExpect(status().is(expectedStatus))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value(code));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"toUserId\":null}", "{\"toUserId\":\"not-a-uuid\"}"})
    void aCheerWithoutAValidFriendIdIsRejected(String body) throws Exception {
        FriendControllerTestSupport.authenticate(authService);
        when(friendPolicy.friendsEnabled()).thenReturn(true);

        mockMvc.perform(sendCheer(body)).andExpect(status().isBadRequest());

        verifyNoInteractions(cheerService);
    }

    @Test
    void sendingRequiresLogin() throws Exception {
        mockMvc.perform(post("/api/cheers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"toUserId\":\"" + FRIEND + "\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_REQUIRED"));
    }

    @Test
    void sendingIsRefusedWhileTheFriendFeatureIsOff() throws Exception {
        FriendControllerTestSupport.authenticate(authService);
        when(friendPolicy.friendsEnabled()).thenReturn(false);

        mockMvc.perform(sendCheer("{\"toUserId\":\"" + FRIEND + "\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("FRIENDS_DISABLED"));

        verifyNoInteractions(cheerService);
    }

    private static MockHttpServletRequestBuilder sendCheer(String body) {
        return post("/api/cheers")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body);
    }
}
