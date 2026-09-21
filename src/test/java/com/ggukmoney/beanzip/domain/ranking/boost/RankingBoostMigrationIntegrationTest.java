package com.ggukmoney.beanzip.domain.ranking.boost;

import com.ggukmoney.beanzip.support.FullStackIntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RankingBoostMigrationIntegrationTest extends FullStackIntegrationTestSupport {
    @Test
    void manualMigrationBuildsSchemaAndRemainsAdditiveWithoutOverwritingOperatorPolicy() throws Exception {
        // This isolated Testcontainers database emulates the pre-BEA-308 schema.
        jdbcTemplate.execute("DROP TABLE ranking_boost_run");
        jdbcTemplate.execute("ALTER TABLE ranking_entry DROP COLUMN ranking_boost_score");
        jdbcTemplate.update("DELETE FROM app_config WHERE config_key = ?", SystemRankingBoostPolicy.KEY);
        String migration = new ClassPathResource("db/manual-system-ranking-boost.sql").getContentAsString(StandardCharsets.UTF_8);
        jdbcTemplate.execute(migration);
        assertThat(jdbcTemplate.queryForObject("SELECT column_default FROM information_schema.columns WHERE table_name='ranking_entry' AND column_name='ranking_boost_score'", String.class)).isEqualTo("0");
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM pg_constraint WHERE conrelid='ranking_boost_run'::regclass AND conname='uq_ranking_boost_run_date'", Long.class)).isEqualTo(1L);
        assertThat(jdbcTemplate.queryForObject("SELECT config_value->>'enabled' FROM app_config WHERE config_key=?", String.class, SystemRankingBoostPolicy.KEY)).isEqualTo("false");
        jdbcTemplate.update("UPDATE app_config SET config_value = jsonb_set(config_value, '{minimumLeaderScore}', '42') WHERE config_key = ?", SystemRankingBoostPolicy.KEY);
        jdbcTemplate.execute(migration);
        assertThat(jdbcTemplate.queryForObject("SELECT config_value->>'minimumLeaderScore' FROM app_config WHERE config_key=?", String.class, SystemRankingBoostPolicy.KEY)).isEqualTo("42");
        jdbcTemplate.execute("INSERT INTO ranking_boost_run(run_date,scheduled_at,event_type,status,created_at) VALUES ('2026-09-18',now(),'SYSTEM_RANKING_BOOST','PLANNED',now())");
        assertThatThrownBy(() -> jdbcTemplate.execute("INSERT INTO ranking_boost_run(run_date,scheduled_at,event_type,status,created_at) VALUES ('2026-09-18',now(),'SYSTEM_RANKING_BOOST','PLANNED',now())"))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbcTemplate.execute("INSERT INTO ranking_boost_run(run_date,scheduled_at,event_type,status,created_at) VALUES ('2026-09-19',now(),'SYSTEM_RANKING_BOOST','APPLIED',now())"))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }
}
