package com.ggukmoney.beanzip.domain.notification.repository;

import com.ggukmoney.beanzip.support.FullStackIntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class WeeklyRankingResetNotificationMigrationIntegrationTest extends FullStackIntegrationTestSupport {

    @Test
    void manualMigrationAddsBatchAndRetrySchemaWithoutReplacingOperatorPolicy() throws Exception {
        jdbcTemplate.execute("ALTER TABLE notification_delivery DROP CONSTRAINT IF EXISTS fk_notification_delivery_weekly_reset_batch");
        jdbcTemplate.execute("ALTER TABLE notification_delivery DROP CONSTRAINT IF EXISTS ck_notification_delivery_attempt_count");
        jdbcTemplate.execute("ALTER TABLE notification_delivery DROP COLUMN IF EXISTS weekly_reset_batch_id");
        jdbcTemplate.execute("ALTER TABLE notification_delivery DROP COLUMN IF EXISTS attempt_count");
        jdbcTemplate.execute("ALTER TABLE notification_delivery DROP COLUMN IF EXISTS next_attempt_at");
        jdbcTemplate.execute("ALTER TABLE notification_delivery DROP COLUMN IF EXISTS last_attempt_at");
        jdbcTemplate.execute("DROP TABLE IF EXISTS weekly_ranking_reset_notification_batch");
        jdbcTemplate.update("DELETE FROM app_config WHERE config_key = ?", "notification.rankChange.minimumDifference");
        String migration = new ClassPathResource("db/manual-weekly-ranking-reset-notification.sql")
                .getContentAsString(StandardCharsets.UTF_8);

        jdbcTemplate.execute(migration);

        assertThat(jdbcTemplate.queryForObject(
                "SELECT config_value::text FROM app_config WHERE config_key = ?",
                String.class, "notification.rankChange.minimumDifference")).isEqualTo("1");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns WHERE table_name='notification_delivery' "
                        + "AND column_name IN ('weekly_reset_batch_id','attempt_count','next_attempt_at','last_attempt_at')",
                Long.class)).isEqualTo(4L);
        jdbcTemplate.update("UPDATE app_config SET config_value = '4'::jsonb WHERE config_key = ?",
                "notification.rankChange.minimumDifference");

        jdbcTemplate.execute(migration);

        assertThat(jdbcTemplate.queryForObject(
                "SELECT config_value::text FROM app_config WHERE config_key = ?",
                String.class, "notification.rankChange.minimumDifference")).isEqualTo("4");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM pg_constraint WHERE conrelid='weekly_ranking_reset_notification_batch'::regclass "
                        + "AND conname='uq_weekly_reset_notification_batch_season'",
                Long.class)).isEqualTo(1L);
    }
}
