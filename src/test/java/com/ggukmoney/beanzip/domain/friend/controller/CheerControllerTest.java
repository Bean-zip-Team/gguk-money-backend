package com.ggukmoney.beanzip.domain.friend.controller;

import com.ggukmoney.beanzip.domain.auth.service.AuthService;
import com.ggukmoney.beanzip.domain.friend.dto.response.CheerAckResponse;
import com.ggukmoney.beanzip.domain.friend.dto.response.CheerReplyResponse;
import com.ggukmoney.beanzip.domain.friend.dto.response.CheerSendResponse;
import com.ggukmoney.beanzip.domain.friend.dto.response.CheerUnreadListResponse;
import com.ggukmoney.beanzip.domain.friend.dto.response.FriendProfileResponse;
import com.ggukmoney.beanzip.domain.friend.entity.Cheer;
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
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.ggukmoney.beanzip.domain.friend.controller.FriendControllerTestSupport.TOKEN;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
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

    @Test
    void listsUnreadCheers() throws Exception {
        UUID userId = FriendControllerTestSupport.authenticate(authService);
        UUID cheerId = UUID.randomUUID();
        when(friendPolicy.friendsEnabled()).thenReturn(true);
        when(cheerService.unread(userId)).thenReturn(new CheerUnreadListResponse(List.of(
                new CheerUnreadListResponse.Item(cheerId, new FriendProfileResponse(FRIEND, "박준호"), Cheer.Kind.CLAP,
                        1240, Instant.parse("2026-11-24T01:12:00Z")))));

        mockMvc.perform(get("/api/cheers/unread").header(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.cheers[0].cheerId").value(cheerId.toString()))
                .andExpect(jsonPath("$.data.cheers[0].from.userId").value(FRIEND.toString()))
                .andExpect(jsonPath("$.data.cheers[0].from.name").value("박준호"))
                .andExpect(jsonPath("$.data.cheers[0].kind").value("CLAP"))
                .andExpect(jsonPath("$.data.cheers[0].tapCountSnapshot").value(1240));
    }

    @Test
    void confirmsCheersAndReportsTheCap() throws Exception {
        UUID userId = FriendControllerTestSupport.authenticate(authService);
        UUID cheerId = UUID.randomUUID();
        when(friendPolicy.friendsEnabled()).thenReturn(true);
        when(cheerService.ack(userId, List.of(cheerId))).thenReturn(new CheerAckResponse(3, true, 138));

        mockMvc.perform(authorizedPost("/api/cheers/ack", "{\"cheerIds\":[\"" + cheerId + "\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.shardsGranted").value(3))
                .andExpect(jsonPath("$.data.capped").value(true))
                .andExpect(jsonPath("$.data.shardBalance").value(138));
    }

    @Test
    void repliesAndReportsEachFriendsResult() throws Exception {
        UUID userId = FriendControllerTestSupport.authenticate(authService);
        UUID cheerId = UUID.randomUUID();
        UUID other = UUID.fromString("44444444-4444-4444-4444-444444444444");
        when(friendPolicy.friendsEnabled()).thenReturn(true);
        when(cheerService.reply(userId, List.of(cheerId))).thenReturn(new CheerReplyResponse(
                List.of(new CheerReplyResponse.Result(FRIEND, CheerReplyResponse.Outcome.SENT),
                        new CheerReplyResponse.Result(other, CheerReplyResponse.Outcome.ALREADY_SENT_TODAY)),
                false, 0, 5));

        mockMvc.perform(authorizedPost("/api/cheers/reply", "{\"cheerIds\":[\"" + cheerId + "\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.results[0].userId").value(FRIEND.toString()))
                .andExpect(jsonPath("$.data.results[0].result").value("SENT"))
                .andExpect(jsonPath("$.data.results[1].result").value("ALREADY_SENT_TODAY"))
                .andExpect(jsonPath("$.data.firstOfDay").value(false))
                .andExpect(jsonPath("$.data.shardsGranted").value(0))
                .andExpect(jsonPath("$.data.cheersLeft").value(5));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/cheers/ack", "/api/cheers/reply"})
    void anIdListThatIsMissingTooLongOrMalformedIsRejected(String path) throws Exception {
        FriendControllerTestSupport.authenticate(authService);
        when(friendPolicy.friendsEnabled()).thenReturn(true);
        String tooMany = IntStream.range(0, 101)
                .mapToObj(i -> "\"" + UUID.randomUUID() + "\"")
                .collect(Collectors.joining(",", "{\"cheerIds\":[", "]}"));

        for (String body : List.of(tooMany, "{}", "{\"cheerIds\":[\"not-a-uuid\"]}", "{\"cheerIds\":[null]}")) {
            mockMvc.perform(authorizedPost(path, body)).andExpect(status().isBadRequest());
        }
        verifyNoInteractions(cheerService);
    }

    @ParameterizedTest
    @CsvSource({"GET, /api/cheers/unread", "POST, /api/cheers/ack", "POST, /api/cheers/reply"})
    void receivedCheerApisNeedLoginAndTheFeatureSwitch(String method, String path) throws Exception {
        String body = "{\"cheerIds\":[]}";
        mockMvc.perform(request(HttpMethod.valueOf(method), path).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());

        FriendControllerTestSupport.authenticate(authService);
        when(friendPolicy.friendsEnabled()).thenReturn(false);
        mockMvc.perform(request(HttpMethod.valueOf(method), path)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("FRIENDS_DISABLED"));
        verifyNoInteractions(cheerService);
    }

    private static MockHttpServletRequestBuilder authorizedPost(String path, String body) {
        return post(path)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body);
    }

    private static MockHttpServletRequestBuilder sendCheer(String body) {
        return post("/api/cheers")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body);
    }
}
