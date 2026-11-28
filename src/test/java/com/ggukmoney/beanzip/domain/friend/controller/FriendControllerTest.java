package com.ggukmoney.beanzip.domain.friend.controller;

import com.ggukmoney.beanzip.domain.auth.service.AuthService;
import com.ggukmoney.beanzip.domain.friend.dto.response.FriendListResponse;
import com.ggukmoney.beanzip.domain.friend.dto.response.FriendProfileResponse;
import com.ggukmoney.beanzip.domain.friend.dto.response.FriendSummaryResponse;
import com.ggukmoney.beanzip.domain.friend.service.FriendAccess;
import com.ggukmoney.beanzip.domain.friend.service.FriendQueryService;
import com.ggukmoney.beanzip.global.common.GlobalExceptionHandler;
import com.ggukmoney.beanzip.global.config.FriendPolicyConfig;
import com.ggukmoney.beanzip.global.interceptor.AuthInterceptor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.UUID;

import static com.ggukmoney.beanzip.domain.friend.controller.FriendControllerTestSupport.TOKEN;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class FriendControllerTest {

    private final AuthService authService = mock(AuthService.class);
    private final FriendPolicyConfig friendPolicy = mock(FriendPolicyConfig.class);
    private final FriendQueryService friendQueryService = mock(FriendQueryService.class);
    private final MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new FriendController(new FriendAccess(friendPolicy), friendQueryService))
            .addInterceptors(new AuthInterceptor(authService))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

    @Test
    void listsFriendsWithTheCheersLeftToday() throws Exception {
        UUID userId = FriendControllerTestSupport.authenticate(authService);
        UUID friendId = UUID.fromString("33333333-3333-3333-3333-333333333333");
        when(friendPolicy.friendsEnabled()).thenReturn(true);
        when(friendQueryService.list(userId)).thenReturn(new FriendListResponse(10, 7, List.of(
                new FriendListResponse.Friend(friendId, "박준호", 1240, null, false))));

        mockMvc.perform(get("/api/friends").header(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.cheerDailyLimit").value(10))
                .andExpect(jsonPath("$.data.cheersLeft").value(7))
                .andExpect(jsonPath("$.data.friends[0].userId").value(friendId.toString()))
                .andExpect(jsonPath("$.data.friends[0].name").value("박준호"))
                .andExpect(jsonPath("$.data.friends[0].todayTapCount").value(1240))
                .andExpect(jsonPath("$.data.friends[0].lastTapDate").value(nullValue()))
                .andExpect(jsonPath("$.data.friends[0].cheeredToday").value(false));
    }

    @Test
    void returnsTheHomeSummary() throws Exception {
        UUID userId = FriendControllerTestSupport.authenticate(authService);
        UUID friendId = UUID.fromString("33333333-3333-3333-3333-333333333333");
        when(friendPolicy.friendsEnabled()).thenReturn(true);
        when(friendQueryService.summary(userId)).thenReturn(new FriendSummaryResponse(
                4, 2, List.of(new FriendProfileResponse(friendId, "박준호")), 3));

        mockMvc.perform(get("/api/friends/summary").header(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.friendCount").value(4))
                .andExpect(jsonPath("$.data.todayTappedCount").value(2))
                .andExpect(jsonPath("$.data.todayTappedFriends[0].userId").value(friendId.toString()))
                .andExpect(jsonPath("$.data.todayTappedFriends[0].name").value("박준호"))
                .andExpect(jsonPath("$.data.unreadCheerCount").value(3));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/friends", "/api/friends/summary"})
    void friendApisNeedLoginAndTheFeatureSwitch(String path) throws Exception {
        mockMvc.perform(get(path)).andExpect(status().isUnauthorized());

        FriendControllerTestSupport.authenticate(authService);
        when(friendPolicy.friendsEnabled()).thenReturn(false);
        mockMvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("FRIENDS_DISABLED"));
        verifyNoInteractions(friendQueryService);
    }
}
