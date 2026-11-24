package com.ggukmoney.beanzip.domain.friend;

import com.ggukmoney.beanzip.domain.friend.service.CheerService;
import com.ggukmoney.beanzip.domain.friend.service.InviteCodeService;
import com.ggukmoney.beanzip.domain.friend.service.InviteService;
import com.ggukmoney.beanzip.domain.notification.repository.NotificationDeliveryRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 운영에 손으로 적용하는 SQL 이 엔티티와 같은 테이블을 만드는지 확인한다 (BEA-390).
 *
 * <p>테스트 DB 는 Hibernate 가 엔티티에서 만든다. 운영은 {@code ddl-auto=validate} 이고 테이블은
 * {@code db/manual-*.sql} 로 만든다. 둘이 어긋나면 테스트는 통과하고 운영에서만 기동이 실패하거나,
 * 유니크 제약이 빠져 {@code ON CONFLICT} 문장이 운영에서만 실패한다.
 *
 * <p>테이블을 지우고 다시 만들기 때문에 이 클래스에는 테스트를 하나만 둔다.
 */
class FriendSchemaMigrationIntegrationTest extends FriendIntegrationTestSupport {

    private static final List<String> TABLES = List.of("shard_ledger", "invite_code", "friendship", "invite", "cheer");
    /** 배포 전에 적용하는 것들. */
    private static final List<String> BEFORE_DEPLOY = List.of(
            "db/manual-bea-390-shard-ledger.sql",
            "db/manual-bea-390-friends.sql",
            "db/manual-notification-type-check.sql");
    private static final String AFTER_DEPLOY = "db/manual-bea-390-friend-mission-activate.sql";

    @Autowired
    private InviteCodeService inviteCodeService;
    @Autowired
    private InviteService inviteService;
    @Autowired
    private CheerService cheerService;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void theManualSqlBuildsWhatTheEntitiesExpectAndTheFeatureRunsOnIt() throws Exception {
        List<Map<String, Object>> columnsFromEntities = columns();
        Map<String, String> indexesFromEntities = indexes();
        assertThat(columnsFromEntities).as("the entity tables exist before they are replaced").hasSizeGreaterThan(30);
        assertThat(indexesFromEntities).containsKeys(
                "uq_invite_code_code", "uq_friendship_pair", "uq_invite_invitee", "uq_invite_inviter_visit",
                "uq_cheer_from_date_to", "ix_cheer_to_read", "ix_shard_ledger_user_created");

        for (String table : TABLES) {
            jdbcTemplate.execute("DROP TABLE " + table);
        }
        givenTheMissionTypeCheckAsProductionHasIt();
        run(BEFORE_DEPLOY);
        assertThat(columns()).containsExactlyElementsOf(columnsFromEntities);

        // A database where an earlier revision of the ledger script was applied has ref_id at 80 characters.
        // CREATE TABLE IF NOT EXISTS would leave it that way, and a draw with a long Idempotency-Key would fail.
        jdbcTemplate.execute("ALTER TABLE shard_ledger ALTER COLUMN ref_id TYPE VARCHAR(80)");
        // Applying everything again, as also happens when a deploy is retried, repairs that and breaks nothing.
        run(BEFORE_DEPLOY);

        assertThat(columns()).containsExactlyElementsOf(columnsFromEntities);
        // Every unique constraint and index the entities declare, with the same columns in the same order.
        // ON CONFLICT (cols) and the "one per day" rules rest on these; a missing one fails only in production.
        assertThat(indexes()).containsAllEntriesOf(indexesFromEntities);

        // The mission rows go in switched off. A version that does not know FRIEND_INVITE reads only active
        // rows; with these active it would fail to load any mission and show an empty list.
        assertThat(jdbcTemplate.queryForList(
                "SELECT code FROM mission_definition WHERE mission_type = 'FRIEND_INVITE' AND active", String.class))
                .isEmpty();
        run(List.of(AFTER_DEPLOY));
        run(List.of(AFTER_DEPLOY));
        assertThat(jdbcTemplate.queryForList(
                "SELECT code FROM mission_definition WHERE mission_type = 'FRIEND_INVITE' AND active "
                        + "ORDER BY target_value", String.class))
                .containsExactly("FRIEND_INVITE_3", "FRIEND_INVITE_10");
        assertThat(jdbcTemplate.queryForList("""
                SELECT conname FROM pg_constraint
                WHERE conrelid = 'mission_definition'::regclass AND contype = 'c'
                  AND pg_get_constraintdef(oid) LIKE '%mission_type%'
                """, String.class))
                .as("both old type checks are replaced, not left beside the new one")
                .containsExactly("ck_mission_definition_type");
        assertThat(jdbcTemplate.queryForObject("""
                SELECT count(*) FROM pg_constraint
                WHERE conrelid = 'mission_definition'::regclass AND conname = 'ck_mission_definition_target'
                """, Long.class))
                .as("a check that is not about the mission type is left alone")
                .isEqualTo(1);

        theWholeFlowRuns();
        thePairOrderCheckIsThere();
        theFriendPushIndexMatchesTheDueQuery();
    }

    /** 코드, 방문, 가입, 친구, 응원, 두 가지 확인까지. 유니크 제약에 기대는 문장이 모두 지나간다. */
    private void theWholeFlowRuns() {
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

        assertThat(jdbcTemplate.queryForList("SELECT reason FROM shard_ledger ORDER BY id", String.class))
                .containsExactly("INVITE_SUCCESS_INVITEE", "CHEER_SENT_FIRST", "INVITE_SUCCESS_INVITER", "CHEER_RECEIVED");
        assertThat(jdbcTemplate.queryForList(
                "SELECT notification_type FROM notification_delivery WHERE user_id = ? ORDER BY id", String.class, inviter))
                .containsExactly("FRIEND_INVITE_SUCCESS", "FRIEND_CHEER");
    }

    /** 이 제약이 없으면 같은 두 사람이 순서만 바뀐 두 행으로 저장될 수 있다. */
    private void thePairOrderCheckIsThere() {
        UUID low = UUID.fromString("00000000-0000-4000-8000-000000000001");
        UUID high = UUID.fromString("ffffffff-ffff-4fff-bfff-ffffffffffff");
        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO friendship (user_low, user_high, source, created_at) VALUES (?, ?, 'INVITE_LINK', now())",
                high, low))
                .hasMessageContaining("ck_friendship_order");
    }

    /**
     * 매분 도는 발송 대상 조회가 수동 SQL 의 부분 인덱스를 쓸 수 있는지 본다.
     *
     * <p>부분 인덱스는 조회의 조건이 인덱스의 조건을 포함할 때만 쓰인다. 조회에서 상태나 종류의 목록이
     * 하나라도 달라지면 인덱스는 있어도 쓰이지 않고, 그 사실은 느려지기 전까지 드러나지 않는다.
     */
    private void theFriendPushIndexMatchesTheDueQuery() throws Exception {
        String contracts = new ClassPathResource("db/manual-index-contracts.sql").getContentAsString(StandardCharsets.UTF_8);
        Matcher statement = Pattern
                .compile("CREATE INDEX CONCURRENTLY ix_notification_delivery_friend_due[^;]+;")
                .matcher(contracts);
        assertThat(statement.find()).isTrue();
        // CONCURRENTLY cannot run inside the statement batch a test sends; the index itself is the same.
        jdbcTemplate.execute(statement.group().replace("CONCURRENTLY ", ""));

        String dueQuery = NotificationDeliveryRepository.class
                .getMethod("findDueFriendDeliveries", Instant.class, Instant.class, int.class)
                .getAnnotation(Query.class).value()
                .replace(":staleBefore", "now() - interval '1 hour'")
                .replace(":now", "now()")
                .replace(":pageSize", "50");
        List<String> plan = new TransactionTemplate(transactionManager).execute(status -> {
            // The table is nearly empty here, so the planner would scan it whatever indexes exist.
            jdbcTemplate.execute("SET LOCAL enable_seqscan = off");
            return jdbcTemplate.queryForList("EXPLAIN " + dueQuery, String.class);
        });
        assertThat(String.join("\n", plan)).contains("ix_notification_delivery_friend_due");
    }

    /**
     * 운영의 미션 종류 CHECK 를 흉내 낸다. 운영은 {@code docs/sql/mission-definition.sql} 로 만들어서 이름이
     * {@code ck_mission_definition_type} 이고 예전 네 종류만 허용한다. 테스트 DB 의 CHECK 는 Hibernate 가
     * 이름을 붙이고 새 종류까지 이미 허용하므로, 그대로 두면 SQL 이 CHECK 를 고치지 않아도 통과한다.
     */
    private void givenTheMissionTypeCheckAsProductionHasIt() {
        jdbcTemplate.queryForList("""
                SELECT conname FROM pg_constraint
                WHERE conrelid = 'mission_definition'::regclass AND contype = 'c'
                  AND pg_get_constraintdef(oid) LIKE '%mission_type%'
                """, String.class)
                .forEach(name -> jdbcTemplate.execute("ALTER TABLE mission_definition DROP CONSTRAINT \"" + name + "\""));
        jdbcTemplate.execute("""
                ALTER TABLE mission_definition ADD CONSTRAINT ck_mission_definition_type
                CHECK (mission_type IN ('ATTENDANCE', 'TAP_COUNT', 'RANK_UP', 'NOTIFICATION_OPT_IN'))
                """);
        // A second copy under the name Hibernate would have given it. The script finds the type check by
        // what it checks, so it must replace this one too, and must leave the other checks alone.
        jdbcTemplate.execute("""
                ALTER TABLE mission_definition ADD CONSTRAINT mission_definition_mission_type_check
                CHECK (mission_type IN ('ATTENDANCE', 'TAP_COUNT', 'RANK_UP', 'NOTIFICATION_OPT_IN'))
                """);
        jdbcTemplate.execute("""
                ALTER TABLE mission_definition ADD CONSTRAINT ck_mission_definition_target CHECK (target_value > 0)
                """);
        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO mission_definition
                    (code, mission_type, period_type, name, target_value, reward_point_amount, sort_order, active,
                     created_at, updated_at)
                VALUES ('PROBE', 'FRIEND_INVITE', 'ONE_TIME', 'probe', 1, 1, 0, FALSE, now(), now())
                """))
                .as("as in production before the script, the new mission type is refused")
                .hasMessageContaining("ck_mission_definition_type");
    }

    private void run(List<String> scripts) throws IOException {
        for (String script : scripts) {
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

    /** 인덱스 이름 → 정의. 유니크 제약도 인덱스로 나온다. */
    private Map<String, String> indexes() {
        return jdbcTemplate.queryForList("""
                SELECT indexname, indexdef
                FROM pg_indexes
                WHERE schemaname = 'public' AND tablename IN ('shard_ledger', 'invite_code', 'friendship', 'invite', 'cheer')
                """).stream()
                .collect(Collectors.toMap(row -> (String) row.get("indexname"), row -> (String) row.get("indexdef")));
    }
}
