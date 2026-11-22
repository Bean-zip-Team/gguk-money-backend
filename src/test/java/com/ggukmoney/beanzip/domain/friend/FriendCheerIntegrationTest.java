package com.ggukmoney.beanzip.domain.friend;

import com.ggukmoney.beanzip.global.config.FriendPolicyConfig;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
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
