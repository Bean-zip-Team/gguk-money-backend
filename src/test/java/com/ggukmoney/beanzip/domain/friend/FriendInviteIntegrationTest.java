package com.ggukmoney.beanzip.domain.friend;

import com.ggukmoney.beanzip.domain.auth.client.TossAuthClient;
import com.ggukmoney.beanzip.domain.friend.dto.response.InviteSignupResponse;
import com.ggukmoney.beanzip.domain.friend.entity.Friendship;
import com.ggukmoney.beanzip.domain.friend.repository.InviteRepository;
import com.ggukmoney.beanzip.domain.friend.service.InviteCodeService;
import com.ggukmoney.beanzip.domain.friend.service.InviteService;
import com.ggukmoney.beanzip.domain.user.entity.AppUser;
import com.ggukmoney.beanzip.domain.user.repository.AppUserRepository;
import com.ggukmoney.beanzip.global.config.FriendPolicyConfig;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 초대 코드, 친구 관계, 초대 흐름을 실제 PostgreSQL 에서 확인한다 (BEA-390). */
class FriendInviteIntegrationTest extends FriendIntegrationTestSupport {

    @Autowired
    private InviteCodeService inviteCodeService;
    @Autowired
    private AppUserRepository appUserRepository;
    @Autowired
    private InviteRepository invites;
    @Autowired
    private InviteService inviteService;

    @MockitoBean
    private TossAuthClient tossAuthClient;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void aUserKeepsOneInviteCodeEvenWhenAskedTwiceAtOnce() throws Exception {
        UUID userId = newUser();
        Callable<String> ask = () -> inviteCodeService.getOrCreate(userId);

        List<Future<String>> answers;
        try (ExecutorService executor = Executors.newFixedThreadPool(4)) {
            answers = executor.invokeAll(List.of(ask, ask, ask, ask));
        }

        String code = inviteCodeService.getOrCreate(userId);
        assertThat(code).matches("[2-9A-HJKMNP-Z]{8}");
        for (Future<String> answer : answers) {
            assertThat(answer.get()).isEqualTo(code);
        }
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM invite_code WHERE user_id = ?", Long.class, userId)).isEqualTo(1);
        assertThat(inviteCodeService.findInviter(code.toLowerCase())).map(AppUser::getId).contains(userId);
        assertThat(inviteCodeService.getOrCreate(newUser())).isNotEqualTo(code);
    }

    @Test
    void aPairOfFriendsIsOneRowWhicheverSideConnects() {
        // Chosen so that Java's signed UUID order disagrees with PostgreSQL's byte order.
        UUID small = newUser(UUID.fromString("00000000-0000-4000-8000-" + randomHex12()));
        UUID large = newUser(UUID.fromString("ffffffff-ffff-4fff-bfff-" + randomHex12()));

        assertThat(friendships.areFriends(small, large)).isFalse();
        assertThat(friendships.connect(large, small, Friendship.Source.INVITE_SIGNUP)).isTrue();
        // The database's own CHECK (user_low < user_high) accepted the order chosen in Java.
        assertThat(friendships.connect(small, large, Friendship.Source.INVITE_LINK)).isFalse();

        assertThat(friendships.areFriends(small, large)).isTrue();
        assertThat(friendships.areFriends(large, small)).isTrue();
        assertThat(jdbcTemplate.queryForMap(
                "SELECT user_low, user_high, source FROM friendship WHERE user_low = ? OR user_high = ?", small, small))
                .containsEntry("user_low", small)
                .containsEntry("user_high", large)
                .containsEntry("source", "INVITE_SIGNUP");
    }

    @Test
    void aLinkVisitBecomesOnePendingInviteHoweverOftenItIsReported() throws Exception {
        UUID inviter = signUp("김민지");
        String code = inviteCodeService.getOrCreate(inviter);
        UUID phone = UUID.randomUUID();
        UUID anotherPhone = UUID.randomUUID();

        visit(code, phone);
        visit(code, phone);
        assertThat(pendingInvites(inviter)).as("the same link opened twice on one phone").isEqualTo(1);

        visit(code, anotherPhone);
        assertThat(pendingInvites(inviter)).isEqualTo(2);

        // No such code: still 204, nothing stored.
        Long before = jdbcTemplate.queryForObject("SELECT count(*) FROM invite", Long.class);
        visit("ZZZZZZZZ", UUID.randomUUID());
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM invite", Long.class)).isEqualTo(before);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM invite WHERE inviter_id = ? AND invitee_id IS NULL AND succeeded_at IS NULL",
                Long.class, inviter)).isEqualTo(2);
    }

    @Test
    void aLinkVisitStoresNothingWhileTheFriendFeatureIsOff() throws Exception {
        UUID inviter = signUp("김민지");
        String code = inviteCodeService.getOrCreate(inviter);
        setConfig(FriendPolicyConfig.KEY_ENABLED, "false");

        visit(code, UUID.randomUUID());

        assertThat(pendingInvites(inviter)).isZero();
    }

    @Test
    void pendingInvitesPerInviterStopAtTheCap() {
        UUID inviter = signUp("김민지");
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);

        for (int i = 0; i < 4; i++) {
            transaction.executeWithoutResult(status -> invites.insertPendingIfAbsent(
                    UUID.randomUUID(), inviter, UUID.randomUUID(), Instant.now(), 3));
        }

        // The endpoint needs no login, so this is what keeps a script from filling the table.
        assertThat(pendingInvites(inviter)).isEqualTo(3);
    }

    @Test
    void aSignupThroughAnInviteLinkMakesFriendsAndRewardsTheInviteeAtOnce() throws Exception {
        UUID inviter = signUp("김민지");
        agreeToFriendPushes(inviter);
        String code = inviteCodeService.getOrCreate(inviter);
        UUID visitKey = UUID.randomUUID();
        visit(code, visitKey);
        int inviterBalance = wallets.getAvailableShardBalance(inviter);

        UUID invitee = signUp("이지우");
        int inviteeBalance = wallets.getAvailableShardBalance(invitee);
        Optional<InviteSignupResponse> result = inviteService.linkOnLogin(invitee, true, code, visitKey.toString());

        assertThat(result).contains(new InviteSignupResponse(inviter, "김민지", 10));
        // The pending visit became the success; no second row.
        Map<String, Object> invite = jdbcTemplate.queryForMap("SELECT * FROM invite WHERE inviter_id = ?", inviter);
        assertThat(invite)
                .containsEntry("status", "SUCCESS")
                .containsEntry("invitee_id", invitee)
                .containsEntry("visit_key", visitKey)
                .containsEntry("inviter_acked_at", null);
        assertThat(invite.get("succeeded_at")).isNotNull();
        assertThat(friendships.areFriends(inviter, invitee)).isTrue();
        // The invitee is paid now; the inviter is paid when they open the success modal.
        assertThat(wallets.getAvailableShardBalance(invitee)).isEqualTo(inviteeBalance + 10);
        assertThat(wallets.getAvailableShardBalance(inviter)).isEqualTo(inviterBalance);
        assertThat(jdbcTemplate.queryForMap(
                "SELECT delta, reason, ref_id, balance_after FROM shard_ledger WHERE user_id = ?", invitee))
                .containsEntry("delta", 10)
                .containsEntry("reason", "INVITE_SUCCESS_INVITEE")
                .containsEntry("ref_id", invite.get("public_id").toString())
                .containsEntry("balance_after", inviteeBalance + 10);
        assertThat(jdbcTemplate.queryForList(
                "SELECT notification_type FROM notification_delivery WHERE user_id = ?", String.class, inviter))
                .containsExactly("FRIEND_INVITE_SUCCESS");

        // The app may report the visit after the login has already used it.
        visit(code, visitKey);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM invite WHERE inviter_id = ?", Long.class, inviter)).isEqualTo(1);
        assertThat(pendingInvites(inviter)).isZero();
    }

    @Test
    void aSecondAccountOnTheSamePhoneIsItsOwnInviteSuccess() {
        UUID inviter = signUp("김민지");
        String code = inviteCodeService.getOrCreate(inviter);
        String phone = UUID.randomUUID().toString();

        UUID first = signUp("이지우");
        UUID second = signUp("박준호");
        assertThat(inviteService.linkOnLogin(first, true, code, phone)).isPresent();
        assertThat(inviteService.linkOnLogin(second, true, code, phone)).isPresent();

        assertThat(jdbcTemplate.queryForList(
                "SELECT invitee_id FROM invite WHERE inviter_id = ? AND status = 'SUCCESS'", UUID.class, inviter))
                .containsExactlyInAnyOrder(first, second);
        assertThat(friendships.areFriends(inviter, second)).isTrue();
        // Being invited counts once per person, whoever's link it is.
        UUID otherInviter = signUp("최다은");
        assertThat(inviteService.linkOnLogin(first, true, inviteCodeService.getOrCreate(otherInviter), null))
                .isEmpty();
        assertThat(friendships.areFriends(otherInviter, first)).isFalse();
    }

    @Test
    void anExistingMemberWhoOpensALinkOnlyBecomesAFriend() throws Exception {
        UUID inviter = signUp("김민지");
        agreeToFriendPushes(inviter);
        String code = inviteCodeService.getOrCreate(inviter);
        UUID member = signUp("이지우");
        int memberBalance = wallets.getAvailableShardBalance(member);
        UUID visitKey = UUID.randomUUID();
        visit(code, visitKey);

        assertThat(inviteService.linkOnLogin(member, false, code, visitKey.toString())).isEmpty();
        assertThat(inviteService.linkOnLogin(member, false, code, visitKey.toString())).isEmpty();

        assertThat(friendships.areFriends(inviter, member)).isTrue();
        assertThat(wallets.getAvailableShardBalance(member)).isEqualTo(memberBalance);
        assertThat(pendingInvites(inviter)).as("the visit stays pending").isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM invite WHERE status = 'SUCCESS' AND inviter_id = ?", Long.class, inviter))
                .isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM notification_delivery WHERE user_id = ?", Long.class, inviter))
                .as("one 'friend connected' push, not one per login").isEqualTo(1);
    }

    @Test
    void theLoginApiCarriesTheInviteThroughToTheResponse() throws Exception {
        UUID inviter = signUp("김민지");
        String code = inviteCodeService.getOrCreate(inviter);
        String userKey = "toss-" + UUID.randomUUID();
        when(tossAuthClient.generateToken("auth-code", "DEFAULT")).thenReturn(new TossAuthClient.TossToken("toss-access"));
        when(tossAuthClient.loginMe("toss-access"))
                .thenReturn(new TossAuthClient.TossLoginMe(userKey, "이지우", null, List.of()));
        // A malformed visitKey must not turn the login into a 400.
        String body = """
                {"authorizationCode":"auth-code","referrer":"DEFAULT","inviteCode":"%s","visitKey":"not-a-uuid"}
                """.formatted(code);

        mockMvc.perform(post("/api/auth/toss/login").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.newUser").value(true))
                .andExpect(jsonPath("$.data.invite.inviterUserId").value(inviter.toString()))
                .andExpect(jsonPath("$.data.invite.inviterName").value("김민지"))
                .andExpect(jsonPath("$.data.invite.shardsGranted").value(10));

        // Logging in again with the same code: already a member, already friends.
        mockMvc.perform(post("/api/auth/toss/login").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.newUser").value(false))
                .andExpect(jsonPath("$.data.invite").value(nullValue()));
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM invite WHERE inviter_id = ?", Long.class, inviter)).isEqualTo(1);

        // An unknown code is ignored and the signup goes through.
        when(tossAuthClient.loginMe("toss-access"))
                .thenReturn(new TossAuthClient.TossLoginMe("toss-" + UUID.randomUUID(), "박준호", null, List.of()));
        mockMvc.perform(post("/api/auth/toss/login").contentType(MediaType.APPLICATION_JSON).content("""
                        {"authorizationCode":"auth-code","referrer":"DEFAULT","inviteCode":"ZZZZZZZZ"}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.newUser").value(true))
                .andExpect(jsonPath("$.data.invite").value(nullValue()));
    }

    @Test
    void theInviterIsPaidWhenTheyConfirmTheSuccessAndOnlyOnce() throws Exception {
        UUID inviter = signUp("김민지");
        String code = inviteCodeService.getOrCreate(inviter);
        UUID invitee = signUp("이지우");
        UUID leaver = signUp("탈퇴할사람");
        inviteService.linkOnLogin(invitee, true, code, null);
        inviteService.linkOnLogin(leaver, true, code, null);
        jdbcTemplate.update("UPDATE app_user SET status = 'WITHDRAWN', withdrawn_at = now() WHERE id = ?", leaver);
        int before = shards(inviter);
        String me = bearer(inviter);

        MvcResult unread = mockMvc.perform(get("/api/invites/success/unread").header(HttpHeaders.AUTHORIZATION, me))
                .andExpect(status().isOk())
                // The invitee who withdrew is left out.
                .andExpect(jsonPath("$.data.invites.length()").value(1))
                .andExpect(jsonPath("$.data.invites[0].friend.userId").value(invitee.toString()))
                .andExpect(jsonPath("$.data.invites[0].friend.name").value("이지우"))
                .andExpect(jsonPath("$.data.invites[0].cheeredToday").value(false))
                .andExpect(jsonPath("$.data.shardsPerInvite").value(10))
                .andReturn();
        String inviteId = JsonPath.read(unread.getResponse().getContentAsString(), "$.data.invites[0].inviteId");
        String leaverInviteId = jdbcTemplate.queryForObject(
                "SELECT public_id::text FROM invite WHERE invitee_id = ?", String.class, leaver);

        // Someone else cannot confirm (and collect) my invite.
        ack(bearer(invitee), inviteId).andExpect(jsonPath("$.data.shardsGranted").value(0));

        // Four confirmations at once, as when the app is opened on two devices: paid once.
        String body = "{\"inviteIds\":[\"%s\",\"%s\",\"%s\"]}".formatted(inviteId, leaverInviteId, UUID.randomUUID());
        Callable<Integer> confirm = () -> JsonPath.read(
                mockMvc.perform(post("/api/invites/success/ack")
                                .header(HttpHeaders.AUTHORIZATION, me)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                        .andReturn().getResponse().getContentAsString(),
                "$.data.shardsGranted");
        int granted = 0;
        try (ExecutorService executor = Executors.newFixedThreadPool(4)) {
            for (Future<Integer> result : executor.invokeAll(List.of(confirm, confirm, confirm, confirm))) {
                granted += result.get();
            }
        }

        assertThat(granted).isEqualTo(10);
        assertThat(shards(inviter)).isEqualTo(before + 10);
        ack(me, inviteId)
                .andExpect(jsonPath("$.data.shardsGranted").value(0))
                .andExpect(jsonPath("$.data.shardBalance").value(before + 10));
        assertThat(jdbcTemplate.queryForMap(
                "SELECT delta, ref_id FROM shard_ledger WHERE user_id = ? AND reason = 'INVITE_SUCCESS_INVITER'", inviter))
                .containsEntry("delta", 10)
                .containsEntry("ref_id", inviteId);
        mockMvc.perform(get("/api/invites/success/unread").header(HttpHeaders.AUTHORIZATION, me))
                .andExpect(jsonPath("$.data.invites.length()").value(0));
        // The withdrawn invitee's success stays unconfirmed and unpaid.
        assertThat(jdbcTemplate.queryForObject(
                "SELECT inviter_acked_at IS NULL FROM invite WHERE invitee_id = ?", Boolean.class, leaver)).isTrue();
    }

    @Test
    void aSuspendedInviterConfirmsWithoutBeingPaid() throws Exception {
        UUID inviter = signUp("김민지");
        UUID invitee = signUp("이지우");
        inviteService.linkOnLogin(invitee, true, inviteCodeService.getOrCreate(inviter), null);
        jdbcTemplate.update("UPDATE app_user SET status = 'SUSPENDED', suspended_at = now() WHERE id = ?", inviter);
        int before = shards(inviter);
        String inviteId = jdbcTemplate.queryForObject(
                "SELECT public_id::text FROM invite WHERE invitee_id = ?", String.class, invitee);

        ack(bearer(inviter), inviteId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.shardsGranted").value(0))
                .andExpect(jsonPath("$.data.shardBalance").value(before));

        assertThat(shards(inviter)).isEqualTo(before);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT inviter_acked_at IS NOT NULL FROM invite WHERE invitee_id = ?", Boolean.class, invitee))
                .as("seen, so the modal does not return on every launch").isTrue();
    }

    @Test
    void aLoggedInMemberAcceptsALinkAndOnlyBecomesAFriend() throws Exception {
        UUID inviter = signUp("김민지");
        agreeToFriendPushes(inviter);
        String code = inviteCodeService.getOrCreate(inviter);
        UUID member = signUp("이지우");
        UUID gone = signUp("탈퇴한초대자");
        String goneCode = inviteCodeService.getOrCreate(gone);
        jdbcTemplate.update("UPDATE app_user SET status = 'WITHDRAWN', withdrawn_at = now() WHERE id = ?", gone);
        int before = shards(member);
        String me = bearer(member);

        accept(me, code)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.friend.userId").value(inviter.toString()))
                .andExpect(jsonPath("$.data.friend.name").value("김민지"))
                .andExpect(jsonPath("$.data.alreadyFriends").value(false));
        accept(me, code).andExpect(status().isOk()).andExpect(jsonPath("$.data.alreadyFriends").value(true));
        accept(me, inviteCodeService.getOrCreate(member))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVITE_SELF"));
        accept(me, "ZZZZZZZZ").andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value("INVITE_CODE_NOT_FOUND"));
        accept(me, goneCode).andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value("INVITE_CODE_NOT_FOUND"));

        assertThat(friendships.areFriends(inviter, member)).isTrue();
        assertThat(shards(member)).isEqualTo(before);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM invite WHERE inviter_id = ?", Long.class, inviter)).isZero();
        assertThat(jdbcTemplate.queryForList(
                "SELECT notification_type FROM notification_delivery WHERE user_id = ?", String.class, inviter))
                .containsExactly("FRIEND_INVITE_SUCCESS");
        // Nothing for the inviter to confirm: an existing member joining is not an invite success.
        mockMvc.perform(get("/api/invites/success/unread").header(HttpHeaders.AUTHORIZATION, bearer(inviter)))
                .andExpect(jsonPath("$.data.invites.length()").value(0));
    }

    private ResultActions ack(String authorization, String inviteId) throws Exception {
        return mockMvc.perform(post("/api/invites/success/ack")
                .header(HttpHeaders.AUTHORIZATION, authorization)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"inviteIds\":[\"" + inviteId + "\"]}"));
    }

    private ResultActions accept(String authorization, String code) throws Exception {
        return mockMvc.perform(post("/api/invites/accept")
                .header(HttpHeaders.AUTHORIZATION, authorization)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"inviteCode\":\"" + code + "\"}"));
    }

    private void visit(String code, UUID visitKey) throws Exception {
        mockMvc.perform(post("/api/invites/visits")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"inviteCode\":\"%s\",\"visitKey\":\"%s\"}".formatted(code, visitKey)))
                .andExpect(status().isNoContent());
    }

    private long pendingInvites(UUID inviter) {
        return jdbcTemplate.queryForObject(
                "SELECT count(*) FROM invite WHERE inviter_id = ? AND status = 'PENDING'", Long.class, inviter);
    }

    private UUID newUser() {
        return appUserRepository.save(AppUser.createActive("friend-" + UUID.randomUUID(), null)).getId();
    }

    /** 원하는 ID 로 사용자를 만든다. 엔티티는 ID 를 스스로 정하므로 저장한 뒤 바꾼다. */
    private UUID newUser(UUID id) {
        UUID generated = newUser();
        jdbcTemplate.update("UPDATE app_user SET id = ? WHERE id = ?", id, generated);
        return id;
    }

    private static String randomHex12() {
        return UUID.randomUUID().toString().substring(24);
    }
}
