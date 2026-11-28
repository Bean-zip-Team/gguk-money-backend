package com.ggukmoney.beanzip.domain.friend.controller;

import com.ggukmoney.beanzip.domain.auth.service.AuthService;
import com.ggukmoney.beanzip.domain.friend.dto.response.FriendProfileResponse;
import com.ggukmoney.beanzip.domain.friend.dto.response.InviteAcceptResponse;
import com.ggukmoney.beanzip.domain.friend.dto.response.InviteAckResponse;
import com.ggukmoney.beanzip.domain.friend.dto.response.InviteSuccessListResponse;
import com.ggukmoney.beanzip.domain.friend.service.FriendAccess;
import com.ggukmoney.beanzip.domain.friend.service.InviteCodeService;
import com.ggukmoney.beanzip.domain.friend.service.InviteService;
import com.ggukmoney.beanzip.global.common.GlobalExceptionHandler;
import com.ggukmoney.beanzip.global.config.FriendPolicyConfig;
import com.ggukmoney.beanzip.global.interceptor.AuthInterceptor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.ggukmoney.beanzip.domain.friend.controller.FriendControllerTestSupport.TOKEN;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
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

    @Test
    void acceptsAnInviteLinkForALoggedInMember() throws Exception {
        UUID userId = FriendControllerTestSupport.authenticate(authService);
        UUID inviterId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        when(friendPolicy.friendsEnabled()).thenReturn(true);
        when(inviteService.accept(userId, "K7P2QX9A"))
                .thenReturn(new InviteAcceptResponse(new FriendProfileResponse(inviterId, "김민지"), false));

        mockMvc.perform(authorized(post("/api/invites/accept")).content("{\"inviteCode\":\"K7P2QX9A\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.friend.userId").value(inviterId.toString()))
                .andExpect(jsonPath("$.data.friend.name").value("김민지"))
                .andExpect(jsonPath("$.data.alreadyFriends").value(false));
    }

    @Test
    void anUnknownInviteCodeIsNotFound() throws Exception {
        UUID userId = FriendControllerTestSupport.authenticate(authService);
        when(friendPolicy.friendsEnabled()).thenReturn(true);
        when(inviteService.accept(userId, "ZZZZZZZZ"))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "INVITE_CODE_NOT_FOUND"));

        mockMvc.perform(authorized(post("/api/invites/accept")).content("{\"inviteCode\":\"ZZZZZZZZ\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("INVITE_CODE_NOT_FOUND"));
    }

    @Test
    void listsUnreadInviteSuccesses() throws Exception {
        UUID userId = FriendControllerTestSupport.authenticate(authService);
        UUID inviteId = UUID.randomUUID();
        UUID friendId = UUID.randomUUID();
        when(friendPolicy.friendsEnabled()).thenReturn(true);
        when(inviteService.unreadSuccesses(userId)).thenReturn(new InviteSuccessListResponse(
                List.of(new InviteSuccessListResponse.Item(inviteId, new FriendProfileResponse(friendId, "이지우"), true)),
                10));

        mockMvc.perform(authorized(get("/api/invites/success/unread")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.invites[0].inviteId").value(inviteId.toString()))
                .andExpect(jsonPath("$.data.invites[0].friend.userId").value(friendId.toString()))
                .andExpect(jsonPath("$.data.invites[0].friend.name").value("이지우"))
                .andExpect(jsonPath("$.data.invites[0].cheeredToday").value(true))
                .andExpect(jsonPath("$.data.shardsPerInvite").value(10));
    }

    @Test
    void acknowledgesInviteSuccesses() throws Exception {
        UUID userId = FriendControllerTestSupport.authenticate(authService);
        UUID inviteId = UUID.randomUUID();
        when(friendPolicy.friendsEnabled()).thenReturn(true);
        when(inviteService.ackSuccesses(userId, List.of(inviteId))).thenReturn(new InviteAckResponse(10, 135));

        mockMvc.perform(authorized(post("/api/invites/success/ack")).content("{\"inviteIds\":[\"" + inviteId + "\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.shardsGranted").value(10))
                .andExpect(jsonPath("$.data.shardBalance").value(135));
    }

    @Test
    void anAckWithMoreThanAHundredIdsOrABadIdIsRejected() throws Exception {
        FriendControllerTestSupport.authenticate(authService);
        when(friendPolicy.friendsEnabled()).thenReturn(true);
        String tooMany = IntStream.range(0, 101)
                .mapToObj(i -> "\"" + UUID.randomUUID() + "\"")
                .collect(Collectors.joining(",", "{\"inviteIds\":[", "]}"));

        for (String body : List.of(tooMany, "{}", "{\"inviteIds\":[\"not-a-uuid\"]}", "{\"inviteIds\":[null]}")) {
            mockMvc.perform(authorized(post("/api/invites/success/ack")).content(body))
                    .andExpect(status().isBadRequest());
        }
        verify(inviteService, never()).ackSuccesses(any(), any());
    }

    @ParameterizedTest
    @CsvSource({"POST, /api/invites/accept", "GET, /api/invites/success/unread", "POST, /api/invites/success/ack"})
    void memberInviteApisNeedLoginAndTheFeatureSwitch(String method, String path) throws Exception {
        String body = path.endsWith("accept") ? "{\"inviteCode\":\"K7P2QX9A\"}" : "{\"inviteIds\":[]}";
        mockMvc.perform(request(HttpMethod.valueOf(method), path).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());

        FriendControllerTestSupport.authenticate(authService);
        when(friendPolicy.friendsEnabled()).thenReturn(false);
        mockMvc.perform(authorized(request(HttpMethod.valueOf(method), path)).content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("FRIENDS_DISABLED"));
        verifyNoInteractions(inviteService);
    }

    private static MockHttpServletRequestBuilder authorized(MockHttpServletRequestBuilder builder) {
        return builder.header(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN).contentType(MediaType.APPLICATION_JSON);
    }
}
