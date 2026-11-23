package com.ggukmoney.beanzip.domain.friend;

import com.ggukmoney.beanzip.global.config.FriendPolicyConfig;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 응원을 API 부터 실제 PostgreSQL 까지 확인한다 (BEA-390). */
class FriendCheerIntegrationTest extends FriendIntegrationTestSupport {

    @Test
    void aCheerGoesOncePerFriendPerDayAndOnlyTheFirstRewardsTheSender() throws Exception {
        UUID me = signUp("김민지");
        UUID friend = signUp("이지우");
        UUID other = signUp("박준호");
        befriend(me, friend);
        befriend(me, other);
        agreeToFriendPushes(friend);
        int mine = shards(me);
        int friends = shards(friend);

        cheer(me, friend)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.cheerId").isNotEmpty())
                .andExpect(jsonPath("$.data.firstOfDay").value(true))
                .andExpect(jsonPath("$.data.shardsGranted").value(1))
                .andExpect(jsonPath("$.data.cheersLeft").value(9));
        cheer(me, friend)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("CHEER_ALREADY_SENT_TODAY"));
        cheer(me, other)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.firstOfDay").value(false))
                .andExpect(jsonPath("$.data.shardsGranted").value(0))
                .andExpect(jsonPath("$.data.cheersLeft").value(8));

        assertThat(shards(me)).isEqualTo(mine + 1);
        // The receiver is paid when they confirm the cheer, not when it is sent.
        assertThat(shards(friend)).isEqualTo(friends);
        Map<String, Object> cheer = jdbcTemplate.queryForMap(
                "SELECT * FROM cheer WHERE from_user_id = ? AND to_user_id = ?", me, friend);
        assertThat(cheer)
                .containsEntry("kind", "DEFAULT")
                .containsEntry("cheer_date", java.sql.Date.valueOf(today()))
                .containsEntry("read_at", null)
                .containsEntry("reward_shards", null);
        assertThat(jdbcTemplate.queryForMap(
                "SELECT delta, reason, ref_id FROM shard_ledger WHERE user_id = ? AND reason = 'CHEER_SENT_FIRST'", me))
                .containsEntry("delta", 1)
                .containsEntry("ref_id", cheer.get("public_id").toString());
        // Only the friend who agreed to friend pushes gets one queued.
        assertThat(jdbcTemplate.queryForList(
                "SELECT notification_type FROM notification_delivery WHERE user_id IN (?, ?)", String.class, friend, other))
                .containsExactly("FRIEND_CHEER");
    }

    @Test
    void theDailyLimitCountsFriendsAndARepeatIsStillReportedAsAlreadySent() throws Exception {
        UUID me = signUp("김민지");
        List<UUID> friendIds = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            UUID friend = signUp("친구" + i);
            befriend(me, friend);
            friendIds.add(friend);
        }
        setConfig(FriendPolicyConfig.KEY_CHEER_DAILY_LIMIT, "2");
        try {
            cheer(me, friendIds.get(0)).andExpect(status().isOk()).andExpect(jsonPath("$.data.cheersLeft").value(1));
            cheer(me, friendIds.get(1)).andExpect(status().isOk()).andExpect(jsonPath("$.data.cheersLeft").value(0));
            cheer(me, friendIds.get(2))
                    .andExpect(status().isTooManyRequests())
                    .andExpect(jsonPath("$.error.code").value("CHEER_DAILY_LIMIT_EXCEEDED"));
            cheer(me, friendIds.get(0))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error.code").value("CHEER_ALREADY_SENT_TODAY"));
        } finally {
            setConfig(FriendPolicyConfig.KEY_CHEER_DAILY_LIMIT, "10");
        }
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM cheer WHERE from_user_id = ?", Long.class, me)).isEqualTo(2);
    }

    @Test
    void theKindIsDecidedFromTheReceiversDay() throws Exception {
        UUID me = signUp("김민지");
        UUID cheeredMeFirst = signUp("돌려줌");
        UUID away = signUp("선물");
        UUID busy = signUp("박수");
        UUID quiet = signUp("기본");
        for (UUID friend : List.of(cheeredMeFirst, away, busy, quiet)) {
            befriend(me, friend);
        }
        setTaps(cheeredMeFirst, today(), 2000);
        setTaps(away, today().minusDays(3), 40);
        setTaps(busy, today(), 1000);
        setTaps(quiet, today().minusDays(2), 40);
        // A row exists for today because they opened the app, but nothing was tapped.
        setTaps(quiet, today(), 0);
        cheer(cheeredMeFirst, me).andExpect(status().isOk());

        for (UUID friend : List.of(cheeredMeFirst, away, busy, quiet)) {
            cheer(me, friend).andExpect(status().isOk());
        }

        assertThat(sentCheer(me, cheeredMeFirst)).containsEntry("kind", "RETURN").containsEntry("tap_count_snapshot", 2000L);
        assertThat(sentCheer(me, away)).containsEntry("kind", "GIFT").containsEntry("tap_count_snapshot", 0L);
        assertThat(sentCheer(me, busy)).containsEntry("kind", "CLAP").containsEntry("tap_count_snapshot", 1000L);
        assertThat(sentCheer(me, quiet)).containsEntry("kind", "DEFAULT").containsEntry("tap_count_snapshot", 0L);
    }

    @Test
    void whoMayCheerWhom() throws Exception {
        UUID me = signUp("김민지");
        UUID stranger = signUp("모르는사람");
        UUID withdrawn = signUp("탈퇴한친구");
        UUID friend = signUp("이지우");
        befriend(me, withdrawn);
        befriend(me, friend);
        jdbcTemplate.update("UPDATE app_user SET status = 'WITHDRAWN', withdrawn_at = now() WHERE id = ?", withdrawn);

        cheer(me, stranger).andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("NOT_FRIENDS"));
        cheer(me, withdrawn).andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("NOT_FRIENDS"));
        cheer(me, me).andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("NOT_FRIENDS"));

        setConfig(FriendPolicyConfig.KEY_ENABLED, "false");
        cheer(me, friend).andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("FRIENDS_DISABLED"));
        setConfig(FriendPolicyConfig.KEY_ENABLED, "true");

        jdbcTemplate.update("UPDATE app_user SET status = 'SUSPENDED', suspended_at = now() WHERE id = ?", me);
        cheer(me, friend)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("ACCOUNT_SUSPENDED"));

        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM cheer WHERE from_user_id = ?", Long.class, me)).isZero();
        // A suspended friend can still be cheered.
        cheer(friend, me).andExpect(status().isOk());
    }

    @Test
    void cheersSentAtTheSameMomentAreCountedOneByOne() throws Exception {
        UUID me = signUp("김민지");
        List<UUID> friendIds = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            UUID friend = signUp("친구" + i);
            befriend(me, friend);
            friendIds.add(friend);
        }
        int before = shards(me);
        String token = bearer(me);
        List<Callable<String>> requests = new ArrayList<>();
        // Six different friends at once, plus the first friend twice more.
        for (UUID friend : List.of(friendIds.get(0), friendIds.get(1), friendIds.get(2), friendIds.get(3),
                friendIds.get(4), friendIds.get(5), friendIds.get(0), friendIds.get(0))) {
            requests.add(() -> cheerWith(token, friend).andReturn().getResponse().getContentAsString());
        }

        List<String> bodies = new ArrayList<>();
        try (ExecutorService executor = Executors.newFixedThreadPool(requests.size())) {
            for (Future<String> response : executor.invokeAll(requests)) {
                bodies.add(response.get());
            }
        }

        assertThat(bodies).filteredOn(body -> body.contains("\"firstOfDay\":true")).hasSize(1);
        assertThat(bodies).filteredOn(body -> body.contains("CHEER_ALREADY_SENT_TODAY")).hasSize(2);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM cheer WHERE from_user_id = ?", Long.class, me)).isEqualTo(6);
        assertThat(shards(me)).as("the first-cheer reward is paid once").isEqualTo(before + 1);
    }

    @Test
    void theFriendListShowsEachFriendsDayAndPutsTheOnesToCheerFirst() throws Exception {
        UUID me = signUp("김민지");
        UUID activeToday = signUp("오늘누름");
        UUID away = signUp("사흘전");
        UUID cheered = signUp("응원함");
        UUID withdrawn = signUp("탈퇴");
        UUID suspended = signUp("정지");
        for (UUID friend : List.of(activeToday, away, cheered, withdrawn, suspended)) {
            befriend(me, friend);
        }
        setTaps(activeToday, today(), 1240);
        setTaps(away, today().minusDays(3), 40);
        setTaps(away, today(), 0);
        setTaps(cheered, today().minusDays(9), 7);
        setTaps(suspended, today().minusDays(1), 5);
        cheer(me, cheered).andExpect(status().isOk());
        jdbcTemplate.update("UPDATE app_user SET status = 'WITHDRAWN', withdrawn_at = now() WHERE id = ?", withdrawn);
        jdbcTemplate.update("UPDATE app_user SET status = 'SUSPENDED', suspended_at = now() WHERE id = ?", suspended);

        mockMvc.perform(get("/api/friends").header(HttpHeaders.AUTHORIZATION, bearer(me)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.cheerDailyLimit").value(10))
                .andExpect(jsonPath("$.data.cheersLeft").value(9))
                // Not cheered yet, away longest first; the cheered friend last; the withdrawn one gone.
                .andExpect(jsonPath("$.data.friends.length()").value(4))
                .andExpect(jsonPath("$.data.friends[0].userId").value(away.toString()))
                .andExpect(jsonPath("$.data.friends[0].name").value("사흘전"))
                .andExpect(jsonPath("$.data.friends[0].todayTapCount").value(0))
                .andExpect(jsonPath("$.data.friends[0].lastTapDate").value(today().minusDays(3).toString()))
                .andExpect(jsonPath("$.data.friends[0].cheeredToday").value(false))
                .andExpect(jsonPath("$.data.friends[1].userId").value(suspended.toString()))
                .andExpect(jsonPath("$.data.friends[1].lastTapDate").value(today().minusDays(1).toString()))
                .andExpect(jsonPath("$.data.friends[2].userId").value(activeToday.toString()))
                .andExpect(jsonPath("$.data.friends[2].todayTapCount").value(1240))
                .andExpect(jsonPath("$.data.friends[2].lastTapDate").value(today().toString()))
                .andExpect(jsonPath("$.data.friends[3].userId").value(cheered.toString()))
                .andExpect(jsonPath("$.data.friends[3].cheeredToday").value(true));

        mockMvc.perform(get("/api/friends/summary").header(HttpHeaders.AUTHORIZATION, bearer(me)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.friendCount").value(4))
                .andExpect(jsonPath("$.data.todayTappedCount").value(1))
                .andExpect(jsonPath("$.data.todayTappedFriends.length()").value(1))
                .andExpect(jsonPath("$.data.todayTappedFriends[0].userId").value(activeToday.toString()))
                .andExpect(jsonPath("$.data.todayTappedFriends[0].name").value("오늘누름"))
                .andExpect(jsonPath("$.data.unreadCheerCount").value(0));
        // The friend I cheered has one unread cheer waiting.
        mockMvc.perform(get("/api/friends/summary").header(HttpHeaders.AUTHORIZATION, bearer(cheered)))
                .andExpect(jsonPath("$.data.friendCount").value(1))
                .andExpect(jsonPath("$.data.unreadCheerCount").value(1));
    }

    @Test
    void aFriendWhoHasNeverTappedHasNoLastTapDate() throws Exception {
        UUID me = signUp("김민지");
        UUID newcomer = signUp("이지우");
        befriend(me, newcomer);

        // Signed up without the onboarding reward, so nothing has been tapped yet.
        mockMvc.perform(get("/api/friends").header(HttpHeaders.AUTHORIZATION, bearer(me)))
                .andExpect(jsonPath("$.data.friends[0].todayTapCount").value(0))
                .andExpect(jsonPath("$.data.friends[0].lastTapDate").value(nullValue()));
    }

    @Test
    void receivedCheersPayThreeEachUpToThirtyOnTheDayTheyAreConfirmed() throws Exception {
        UUID me = signUp("김민지");
        List<UUID> senders = new ArrayList<>();
        for (int i = 0; i < 11; i++) {
            UUID friend = signUp("친구" + i);
            befriend(me, friend);
            cheer(friend, me).andExpect(status().isOk());
            senders.add(friend);
        }
        int before = shards(me);
        String token = bearer(me);

        MvcResult unread = mockMvc.perform(get("/api/cheers/unread").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.cheers.length()").value(11))
                // Oldest first.
                .andExpect(jsonPath("$.data.cheers[0].from.userId").value(senders.get(0).toString()))
                .andExpect(jsonPath("$.data.cheers[0].from.name").value("친구0"))
                .andExpect(jsonPath("$.data.cheers[0].kind").value("DEFAULT"))
                .andExpect(jsonPath("$.data.cheers[0].tapCountSnapshot").value(0))
                .andExpect(jsonPath("$.data.cheers[0].createdAt").isNotEmpty())
                .andExpect(jsonPath("$.data.cheers[10].from.userId").value(senders.get(10).toString()))
                .andReturn();
        List<String> cheerIds = JsonPath.read(unread.getResponse().getContentAsString(), "$.data.cheers[*].cheerId");

        ack(token, cheerIds)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.shardsGranted").value(30))
                .andExpect(jsonPath("$.data.capped").value(true))
                .andExpect(jsonPath("$.data.shardBalance").value(before + 30));

        assertThat(shards(me)).isEqualTo(before + 30);
        // Ten cheers paid three each, the eleventh is read but worth nothing, and that does not come back.
        assertThat(jdbcTemplate.queryForList(
                "SELECT reward_shards FROM cheer WHERE to_user_id = ? AND read_at IS NOT NULL ORDER BY created_at, id",
                Integer.class, me))
                .containsExactly(3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 0);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM shard_ledger WHERE user_id = ? AND reason = 'CHEER_RECEIVED' AND delta = 3",
                Long.class, me)).isEqualTo(10);
        ack(token, cheerIds)
                .andExpect(jsonPath("$.data.shardsGranted").value(0))
                .andExpect(jsonPath("$.data.capped").value(false));
        mockMvc.perform(get("/api/cheers/unread").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(jsonPath("$.data.cheers.length()").value(0));
        mockMvc.perform(get("/api/friends/summary").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(jsonPath("$.data.unreadCheerCount").value(0));

        // The cap belongs to the day of confirming: once those confirmations are yesterday's, a new cheer pays.
        jdbcTemplate.update("UPDATE cheer SET read_at = read_at - interval '1 day' WHERE to_user_id = ?", me);
        UUID late = signUp("늦은친구");
        befriend(me, late);
        cheer(late, me).andExpect(status().isOk());
        String lateCheerId = jdbcTemplate.queryForObject(
                "SELECT public_id::text FROM cheer WHERE from_user_id = ? AND to_user_id = ?", String.class, late, me);
        ack(token, List.of(lateCheerId))
                .andExpect(jsonPath("$.data.shardsGranted").value(3))
                .andExpect(jsonPath("$.data.capped").value(false));
    }

    @Test
    void confirmingTheSameCheersAtOnceOrSomeoneElsesPaysOnlyTheReceiverOnce() throws Exception {
        UUID me = signUp("김민지");
        UUID outsider = signUp("남");
        List<String> cheerIds = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            UUID friend = signUp("친구" + i);
            befriend(me, friend);
            cheerIds.add(JsonPath.read(
                    cheer(friend, me).andReturn().getResponse().getContentAsString(), "$.data.cheerId"));
        }
        int before = shards(me);
        int outsiderBefore = shards(outsider);
        String token = bearer(me);

        ack(bearer(outsider), cheerIds).andExpect(jsonPath("$.data.shardsGranted").value(0));

        String body = "{\"cheerIds\":[" + String.join(",", cheerIds.stream().map(id -> "\"" + id + "\"").toList()) + "]}";
        Callable<Integer> confirm = () -> JsonPath.read(
                mockMvc.perform(post("/api/cheers/ack")
                                .header(HttpHeaders.AUTHORIZATION, token)
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

        assertThat(granted).isEqualTo(9);
        assertThat(shards(me)).isEqualTo(before + 9);
        assertThat(shards(outsider)).isEqualTo(outsiderBefore);
    }

    @Test
    void replyingCheersEveryoneBackUnderTheSameRulesWithoutConfirmingAnything() throws Exception {
        UUID me = signUp("김민지");
        UUID first = signUp("첫째");
        UUID second = signUp("둘째");
        UUID third = signUp("셋째");
        UUID leaver = signUp("탈퇴할친구");
        List<String> cheerIds = new ArrayList<>();
        for (UUID friend : List.of(first, second, third, leaver)) {
            befriend(me, friend);
            cheerIds.add(JsonPath.read(
                    cheer(friend, me).andReturn().getResponse().getContentAsString(), "$.data.cheerId"));
        }
        jdbcTemplate.update("UPDATE app_user SET status = 'WITHDRAWN', withdrawn_at = now() WHERE id = ?", leaver);
        int before = shards(me);
        String token = bearer(me);
        setConfig(FriendPolicyConfig.KEY_CHEER_DAILY_LIMIT, "2");
        try {
            reply(token, cheerIds)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.results.length()").value(4))
                    .andExpect(jsonPath("$.data.results[0].userId").value(first.toString()))
                    .andExpect(jsonPath("$.data.results[0].result").value("SENT"))
                    .andExpect(jsonPath("$.data.results[1].result").value("SENT"))
                    .andExpect(jsonPath("$.data.results[2].userId").value(third.toString()))
                    .andExpect(jsonPath("$.data.results[2].result").value("DAILY_LIMIT"))
                    .andExpect(jsonPath("$.data.results[3].result").value("NOT_FRIENDS"))
                    .andExpect(jsonPath("$.data.firstOfDay").value(true))
                    .andExpect(jsonPath("$.data.shardsGranted").value(1))
                    .andExpect(jsonPath("$.data.cheersLeft").value(0));
            reply(token, cheerIds)
                    .andExpect(jsonPath("$.data.results[0].result").value("ALREADY_SENT_TODAY"))
                    .andExpect(jsonPath("$.data.results[1].result").value("ALREADY_SENT_TODAY"))
                    .andExpect(jsonPath("$.data.results[2].result").value("DAILY_LIMIT"))
                    .andExpect(jsonPath("$.data.firstOfDay").value(false))
                    .andExpect(jsonPath("$.data.shardsGranted").value(0));
        } finally {
            setConfig(FriendPolicyConfig.KEY_CHEER_DAILY_LIMIT, "10");
        }

        assertThat(shards(me)).as("only the first-cheer shard; received cheers are not confirmed here").isEqualTo(before + 1);
        // Cheering back someone who cheered me today is a RETURN.
        assertThat(jdbcTemplate.queryForList(
                "SELECT kind FROM cheer WHERE from_user_id = ? ORDER BY id", String.class, me))
                .containsExactly("RETURN", "RETURN");
        // Still unread: the withdrawn sender's cheer is hidden, the other three wait to be confirmed.
        mockMvc.perform(get("/api/cheers/unread").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(jsonPath("$.data.cheers.length()").value(3));
        // Someone else's cheer ids are not mine to reply to.
        reply(bearer(first), cheerIds).andExpect(jsonPath("$.data.results.length()").value(0));
    }

    private ResultActions ack(String authorization, List<String> cheerIds) throws Exception {
        return postIds("/api/cheers/ack", authorization, cheerIds);
    }

    private ResultActions reply(String authorization, List<String> cheerIds) throws Exception {
        return postIds("/api/cheers/reply", authorization, cheerIds);
    }

    private ResultActions postIds(String path, String authorization, List<String> cheerIds) throws Exception {
        String ids = String.join(",", cheerIds.stream().map(id -> "\"" + id + "\"").toList());
        return mockMvc.perform(post(path)
                .header(HttpHeaders.AUTHORIZATION, authorization)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"cheerIds\":[" + ids + "]}"));
    }

    private ResultActions cheer(UUID from, UUID to) throws Exception {
        return cheerWith(bearer(from), to);
    }

    private ResultActions cheerWith(String authorization, UUID to) throws Exception {
        return mockMvc.perform(post("/api/cheers")
                .header(HttpHeaders.AUTHORIZATION, authorization)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"toUserId\":\"" + to + "\"}"));
    }

    private Map<String, Object> sentCheer(UUID from, UUID to) {
        return jdbcTemplate.queryForMap(
                "SELECT kind, tap_count_snapshot FROM cheer WHERE from_user_id = ? AND to_user_id = ?", from, to);
    }
}
