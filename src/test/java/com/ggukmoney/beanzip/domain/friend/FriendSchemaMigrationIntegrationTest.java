package com.ggukmoney.beanzip.domain.friend;

import com.ggukmoney.beanzip.domain.friend.service.CheerService;
import com.ggukmoney.beanzip.domain.friend.service.InviteCodeService;
import com.ggukmoney.beanzip.domain.friend.service.InviteService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 운영에 손으로 적용하는 SQL 이 엔티티와 같은 테이블을 만드는지 확인한다 (BEA-390).
 *
 * <p>테스트 DB 는 Hibernate 가 엔티티에서 만든다. 운영은 {@code ddl-auto=validate} 이고 테이블은
 * {@code db/manual-*.sql} 로 만든다. 둘이 어긋나면 테스트는 통과하고 운영에서만 기동이 실패하거나,
 * 유니크 제약이 빠져 {@code ON CONFLICT} 문장이 운영에서만 실패한다.
 */
class FriendSchemaMigrationIntegrationTest extends FriendIntegrationTestSupport {

    private static final List<String> TABLES = List.of("shard_ledger", "invite_code", "friendship", "invite", "cheer");
    private static final List<String> SCRIPTS = List.of(
            "db/manual-bea-390-shard-ledger.sql",
            "db/manual-bea-390-friends.sql",
            "db/manual-notification-type-check.sql");

    @Autowired
    private InviteCodeService inviteCodeService;
    @Autowired
    private InviteService inviteService;
    @Autowired
    private CheerService cheerService;

    @Test
    void theManualSqlBuildsTheSameTablesAsTheEntitiesAndTheFeatureRunsOnThem() throws IOException {
        List<Map<String, Object>> fromEntities = columns();
        assertThat(fromEntities).as("the entity tables exist before they are replaced").hasSizeGreaterThan(30);

        for (String table : TABLES) {
            jdbcTemplate.execute("DROP TABLE " + table);
        }
        runScripts();
        // Applying it again, as happens when a deploy is retried, changes nothing and fails nothing.
        runScripts();

        assertThat(columns()).containsExactlyElementsOf(fromEntities);

        // The whole flow on the tables the SQL made. Every insert that relies on a unique constraint
        // (ON CONFLICT) would fail here if the SQL had left that constraint out.
        UUID inviter = signUp("김민지");
        agreeToFriendPushes(inviter);
        String code = inviteCodeService.getOrCreate(inviter);
        assertThat(inviteCodeService.getOrCreate(inviter)).isEqualTo(code);
        UUID visitKey = UUID.randomUUID();
        inviteService.recordVisit(code, visitKey);
        inviteService.recordVisit(code, visitKey);
        UUID invitee = signUp("이지우");
        assertThat(inviteService.linkOnLogin(invitee, true, code, visitKey.toString())).isPresent();
        assertThat(friendships.areFriends(inviter, invitee)).isTrue();
        assertThat(cheerService.send(invitee, inviter).firstOfDay()).isTrue();
        UUID inviteId = jdbcTemplate.queryForObject(
                "SELECT public_id FROM invite WHERE invitee_id = ?", UUID.class, invitee);
        assertThat(inviteService.ackSuccesses(inviter, List.of(inviteId)).shardsGranted()).isEqualTo(10);
        UUID cheerId = jdbcTemplate.queryForObject(
                "SELECT public_id FROM cheer WHERE to_user_id = ?", UUID.class, inviter);
        assertThat(cheerService.ack(inviter, List.of(cheerId)).shardsGranted()).isEqualTo(3);

        assertThat(jdbcTemplate.queryForList(
                "SELECT reason FROM shard_ledger ORDER BY id", String.class))
                .containsExactly("INVITE_SUCCESS_INVITEE", "CHEER_SENT_FIRST", "INVITE_SUCCESS_INVITER", "CHEER_RECEIVED");
        assertThat(jdbcTemplate.queryForList(
                "SELECT notification_type FROM notification_delivery WHERE user_id = ? ORDER BY id", String.class, inviter))
                .containsExactly("FRIEND_INVITE_SUCCESS", "FRIEND_CHEER");
        assertThat(jdbcTemplate.queryForList(
                "SELECT code FROM mission_definition WHERE mission_type = 'FRIEND_INVITE' ORDER BY target_value",
                String.class))
                .containsExactly("FRIEND_INVITE_3", "FRIEND_INVITE_10");

        // The SQL also carries the pair-order check. Without it the same two people could be stored
        // twice, once in each order.
        UUID low = UUID.fromString("00000000-0000-4000-8000-000000000001");
        UUID high = UUID.fromString("ffffffff-ffff-4fff-bfff-ffffffffffff");
        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO friendship (user_low, user_high, source, created_at) VALUES (?, ?, 'INVITE_LINK', now())",
                high, low))
                .hasMessageContaining("ck_friendship_order");
    }

    private void runScripts() throws IOException {
        for (String script : SCRIPTS) {
            jdbcTemplate.execute(new ClassPathResource(script).getContentAsString(StandardCharsets.UTF_8));
        }
    }

    private List<Map<String, Object>> columns() {
        return jdbcTemplate.queryForList("""
                SELECT table_name, column_name, data_type, is_nullable, character_maximum_length
                FROM information_schema.columns
                WHERE table_schema = 'public' AND table_name IN ('shard_ledger', 'invite_code', 'friendship', 'invite', 'cheer')
                ORDER BY table_name, column_name
                """);
    }
}
