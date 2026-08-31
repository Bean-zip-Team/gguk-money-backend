package com.ggukmoney.beanzip.support;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class SchemaIndexContractIntegrationTest extends FullStackIntegrationTestSupport {

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void generatedSchemaContainsRepresentableIndexContracts() {
        Map<String, String> indexes = jdbcTemplate.query(
                """
                        select indexname, indexdef
                        from pg_indexes
                        where schemaname = 'public'
                          and indexname in (
                            'ix_app_user_status',
                            'ix_keycap_active_sort',
                            'ix_user_keycap_user_status',
                            'ix_keycap_box_open_user_time'
                          )
                        """,
                resultSet -> {
                    Map<String, String> result = new java.util.HashMap<>();
                    while (resultSet.next()) {
                        result.put(resultSet.getString("indexname"), resultSet.getString("indexdef"));
                    }
                    return result;
                }
        );

        assertThat(indexes).containsOnlyKeys(
                "ix_app_user_status",
                "ix_keycap_active_sort",
                "ix_user_keycap_user_status",
                "ix_keycap_box_open_user_time"
        );
        assertThat(indexes.get("ix_app_user_status")).contains("(status)");
        assertThat(indexes.get("ix_keycap_active_sort")).contains("(active, sort_order)");
        assertThat(indexes.get("ix_user_keycap_user_status")).contains("(user_id, status)");
        assertThat(indexes.get("ix_keycap_box_open_user_time")).contains("user_id, opened_at DESC");
    }

    @Test
    void manualOnlyPartialIndexesHaveCanonicalDdl() throws IOException {
        ClassPathResource resource = new ClassPathResource("db/manual-index-contracts.sql");

        assertThat(resource.exists()).isTrue();
        String ddl = resource.getContentAsString(StandardCharsets.UTF_8);
        assertThat(ddl).contains(
                "CREATE UNIQUE INDEX CONCURRENTLY ux_app_user_active_nickname_normalized",
                "WHERE nickname_normalized IS NOT NULL",
                "AND status = 'ACTIVE'",
                "CREATE UNIQUE INDEX CONCURRENTLY ux_user_keycap_equipped",
                "WHERE equipped = true",
                "CREATE UNIQUE INDEX CONCURRENTLY uq_keycap_box_open_ad_reward_id",
                "WHERE ad_reward_id IS NOT NULL",
                "CREATE INDEX CONCURRENTLY ix_notification_preference_sendable_type_id",
                "ON notification_preference (notification_type, id)",
                "INCLUDE (user_id)",
                "WHERE enabled = true",
                "AND agreement_status = 'AGREED'",
                "CREATE INDEX CONCURRENTLY ix_notification_delivery_sent_cooldown",
                "ON notification_delivery (user_id, notification_type, requested_at DESC)",
                "WHERE status = 'SENT'"
        );
    }

    @Test
    void activeNicknamePartialUniqueRejectsOneOfTwoConcurrentWrites() throws Exception {
        jdbcTemplate.execute("""
                CREATE UNIQUE INDEX ux_app_user_active_nickname_normalized
                ON app_user (nickname_normalized)
                WHERE nickname_normalized IS NOT NULL AND status = 'ACTIVE'
                """);
        UUID firstId = insertUser("first-" + UUID.randomUUID());
        UUID secondId = insertUser("second-" + UUID.randomUUID());
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        List<CompletableFuture<Boolean>> writes = List.of(firstId, secondId).stream()
                .map(userId -> CompletableFuture.supplyAsync(() -> updateNickname(userId, ready, start)))
                .toList();
        assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
        start.countDown();

        assertThat(writes.stream().map(CompletableFuture::join).toList())
                .containsExactlyInAnyOrder(true, false);
    }

    @Test
    void notificationPartialIndexesHaveExpectedDefinitionsAndExecutablePlans() {
        String preferenceExplain = """
                EXPLAIN (ANALYZE, BUFFERS, VERBOSE)
                SELECT user_id
                FROM notification_preference
                WHERE notification_type = 'DAILY_REMINDER'
                  AND enabled = true
                  AND agreement_status = 'AGREED'
                  AND id > 0
                ORDER BY id ASC
                LIMIT 100
                """;
        String cooldownExplain = """
                EXPLAIN (ANALYZE, BUFFERS, VERBOSE)
                SELECT DISTINCT user_id
                FROM notification_delivery
                WHERE user_id IN ('00000000-0000-0000-0000-000000000001'::uuid)
                  AND notification_type = 'RANK_CHANGE'
                  AND status = 'SENT'
                  AND requested_at > now() - interval '6 hours'
                """;
        List<String> preferencePlanBefore = jdbcTemplate.queryForList(preferenceExplain, String.class);
        List<String> cooldownPlanBefore = jdbcTemplate.queryForList(cooldownExplain, String.class);

        jdbcTemplate.execute("""
                CREATE INDEX ix_notification_preference_sendable_type_id
                ON notification_preference (notification_type, id)
                INCLUDE (user_id)
                WHERE enabled = true AND agreement_status = 'AGREED'
                """);
        jdbcTemplate.execute("""
                CREATE INDEX ix_notification_delivery_sent_cooldown
                ON notification_delivery (user_id, notification_type, requested_at DESC)
                WHERE status = 'SENT'
                """);
        jdbcTemplate.execute("ANALYZE notification_preference");
        jdbcTemplate.execute("ANALYZE notification_delivery");

        Map<String, String> indexes = jdbcTemplate.query(
                """
                        select indexname, indexdef
                        from pg_indexes
                        where schemaname = 'public'
                          and indexname in (
                            'ix_notification_preference_sendable_type_id',
                            'ix_notification_delivery_sent_cooldown'
                          )
                        """,
                resultSet -> {
                    Map<String, String> result = new java.util.HashMap<>();
                    while (resultSet.next()) {
                        result.put(resultSet.getString("indexname"), resultSet.getString("indexdef"));
                    }
                    return result;
                }
        );
        assertThat(indexes.get("ix_notification_preference_sendable_type_id"))
                .contains("(notification_type, id) INCLUDE (user_id)")
                .contains("WHERE ((enabled = true) AND ((agreement_status)::text = 'AGREED'::text))");
        assertThat(indexes.get("ix_notification_delivery_sent_cooldown"))
                .contains("(user_id, notification_type, requested_at DESC)")
                .contains("WHERE ((status)::text = 'SENT'::text)");

        List<String> preferencePlan = jdbcTemplate.queryForList(preferenceExplain, String.class);
        List<String> cooldownPlan = jdbcTemplate.queryForList(cooldownExplain, String.class);

        System.out.printf("BEA-251 preference plan before:%n%s%nBEA-251 preference plan after:%n%s%n",
                String.join(System.lineSeparator(), preferencePlanBefore),
                String.join(System.lineSeparator(), preferencePlan));
        System.out.printf("BEA-251 cooldown plan before:%n%s%nBEA-251 cooldown plan after:%n%s%n",
                String.join(System.lineSeparator(), cooldownPlanBefore),
                String.join(System.lineSeparator(), cooldownPlan));

        assertThat(preferencePlan).anyMatch(line -> line.contains("notification_preference"));
        assertThat(cooldownPlan).anyMatch(line -> line.contains("notification_delivery"));
    }

    private UUID insertUser(String nickname) {
        UUID userId = UUID.randomUUID();
        jdbcTemplate.update("""
                insert into app_user (
                    id, status, nickname, nickname_normalized, onboarding_reward_claimed, created_at, updated_at
                ) values (?, 'ACTIVE', ?, ?, false, now(), now())
                """, userId, nickname, nickname);
        return userId;
    }

    private boolean updateNickname(UUID userId, CountDownLatch ready, CountDownLatch start) {
        try {
            new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                ready.countDown();
                await(start);
                jdbcTemplate.update(
                        "update app_user set nickname = 'same', nickname_normalized = 'same' where id = ?",
                        userId
                );
            });
            return true;
        } catch (DataIntegrityViolationException exception) {
            return false;
        }
    }

    private void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("concurrent nickname test timed out");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("concurrent nickname test interrupted", exception);
        }
    }
}
