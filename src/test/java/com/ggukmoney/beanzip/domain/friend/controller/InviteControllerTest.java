package com.ggukmoney.beanzip.domain.friend.controller;

import com.ggukmoney.beanzip.domain.auth.service.AuthService;
import com.ggukmoney.beanzip.domain.friend.service.FriendAccess;
import com.ggukmoney.beanzip.domain.friend.service.InviteCodeService;
import com.ggukmoney.beanzip.domain.friend.service.InviteService;
import com.ggukmoney.beanzip.global.common.GlobalExceptionHandler;
import com.ggukmoney.beanzip.global.config.FriendPolicyConfig;
import com.ggukmoney.beanzip.global.interceptor.AuthInterceptor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.UUID;

import static com.ggukmoney.beanzip.domain.friend.controller.FriendControllerTestSupport.TOKEN;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class InviteControllerTest {

    private final AuthService authService = mock(AuthService.class);
    private final FriendPolicyConfig friendPolicy = mock(FriendPolicyConfig.class);
    private final InviteCodeService inviteCodeService = mock(InviteCodeService.class);
    private final InviteService inviteService = mock(InviteService.class);
    private final MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new InviteController(new FriendAccess(friendPolicy), inviteCodeService, inviteService))
            .addInterceptors(new AuthInterceptor(authService))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

    @Test
    void aLinkVisitIsAcceptedWithoutLogin() throws Exception {
        UUID visitKey = UUID.fromString("3f6c2a9e-8d1b-4c7e-9a52-1b0d6e4f7a21");

        mockMvc.perform(post("/api/invites/visits")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"inviteCode":"K7P2QX9A","visitKey":"3f6c2a9e-8d1b-4c7e-9a52-1b0d6e4f7a21"}
                                """))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        verify(inviteService).recordVisit("K7P2QX9A", visitKey);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{}",
            "{\"inviteCode\":\"K7P2QX9A\"}",
            "{\"visitKey\":\"3f6c2a9e-8d1b-4c7e-9a52-1b0d6e4f7a21\"}",
            "{\"inviteCode\":\"\",\"visitKey\":\"3f6c2a9e-8d1b-4c7e-9a52-1b0d6e4f7a21\"}",
            "{\"inviteCode\":\"K7P2 QX9A\",\"visitKey\":\"3f6c2a9e-8d1b-4c7e-9a52-1b0d6e4f7a21\"}",
            "{\"inviteCode\":\"K7P2QX9A';--\",\"visitKey\":\"3f6c2a9e-8d1b-4c7e-9a52-1b0d6e4f7a21\"}",
            "{\"inviteCode\":\"AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA\",\"visitKey\":\"3f6c2a9e-8d1b-4c7e-9a52-1b0d6e4f7a21\"}",
            "{\"inviteCode\":\"K7P2QX9A\",\"visitKey\":\"not-a-uuid\"}",
    })
    void aMalformedVisitIsRejected(String body) throws Exception {
        mockMvc.perform(post("/api/invites/visits").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));

        verifyNoInteractions(inviteService);
    }

    @Test
    void returnsMyInviteCode() throws Exception {
        UUID userId = FriendControllerTestSupport.authenticate(authService);
        when(friendPolicy.friendsEnabled()).thenReturn(true);
        when(inviteCodeService.getOrCreate(userId)).thenReturn("K7P2QX9A");

        mockMvc.perform(get("/api/invites/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.code").value("K7P2QX9A"));
    }

    @Test
    void myCodeRequiresLogin() throws Exception {
        mockMvc.perform(get("/api/invites/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_REQUIRED"));
    }

    @Test
    void myCodeIsRefusedWhileTheFriendFeatureIsOff() throws Exception {
        FriendControllerTestSupport.authenticate(authService);
        when(friendPolicy.friendsEnabled()).thenReturn(false);

        mockMvc.perform(get("/api/invites/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("FRIENDS_DISABLED"));

        // Nothing is issued for a feature that is switched off.
        verifyNoInteractions(inviteCodeService);
    }
}
