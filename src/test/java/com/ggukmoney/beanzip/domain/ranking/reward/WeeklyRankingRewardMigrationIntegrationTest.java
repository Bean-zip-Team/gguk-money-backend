package com.ggukmoney.beanzip.domain.ranking.reward;

import com.ggukmoney.beanzip.support.FullStackIntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class WeeklyRankingRewardMigrationIntegrationTest extends FullStackIntegrationTestSupport {

    @Test
    void manualMigrationCreatesPendingClaimedExpiredSchemaAndKeepsPolicyDisabled() throws Exception {
        jdbcTemplate.execute("DROP TABLE weekly_ranking_reward");
        jdbcTemplate.update("DELETE FROM app_config WHERE config_key = ?", WeeklyRankingRewardPolicy.KEY);

        String migration = new ClassPathResource("db/manual-weekly-ranking-reward.sql")
                .getContentAsString(StandardCharsets.UTF_8);
        jdbcTemplate.execute(migration);

        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM information_schema.columns
                WHERE table_name = 'weekly_ranking_reward' AND column_name = 'viewed_at'
                """, Long.class)).isEqualTo(1L);
        String statusConstraint = jdbcTemplate.queryForObject("""
                SELECT pg_get_constraintdef(oid)
                FROM pg_constraint
                WHERE conrelid = 'weekly_ranking_reward'::regclass
                  AND conname = 'ck_weekly_ranking_reward_status'
                """, String.class);
        assertThat(statusConstraint).contains("PENDING", "CLAIMED", "EXPIRED").doesNotContain("OPENED");
        assertThat(jdbcTemplate.queryForObject("""
                SELECT config_value->>'enabled' FROM app_config
                WHERE config_key = ? ORDER BY effective_at DESC, id DESC LIMIT 1
                """, String.class, WeeklyRankingRewardPolicy.KEY)).isEqualTo("false");
    }
}
