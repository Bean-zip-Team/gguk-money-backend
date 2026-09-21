package com.ggukmoney.beanzip.domain.ranking.boost;

import com.ggukmoney.beanzip.support.FullStackIntegrationTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataAccessException;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RankingPolicyRolloutMigrationIntegrationTest extends FullStackIntegrationTestSupport {

    private static final List<UUID> INTERNAL_USER_IDS = List.of(
            UUID.fromString("3368d399-1ea6-4609-b999-db853f6d494a"),
            UUID.fromString("7dc0edad-f5ed-41a9-b828-4167c6646569"),
            UUID.fromString("06b310bf-ea66-425f-ac6d-5ed0edcd3d3e"),
            UUID.fromString("4b179019-3cd6-46c8-ad52-36517831f662"),
            UUID.fromString("c115f184-fbaf-4ec0-9fe0-66734c985b57"),
            UUID.fromString("98d72dd1-f642-4332-85b7-8261ca828a5c")
    );

    @BeforeEach
    void cleanRolloutFixtures() {
        jdbcTemplate.update("DELETE FROM app_config WHERE config_key IN (?, ?)",
                "ranking.weeklyReward.policy", SystemRankingBoostPolicy.KEY);
        jdbcTemplate.update("DELETE FROM app_user WHERE id IN (?, ?, ?, ?, ?, ?)", INTERNAL_USER_IDS.toArray());
    }

    @Test
    void rolloutValidatesInternalAccountsAndAppendsDisabledPolicies() throws Exception {
        for (UUID userId : INTERNAL_USER_IDS) {
            insertActiveUser(userId);
        }

        jdbcTemplate.execute(rolloutSql());

        assertThat(jdbcTemplate.queryForObject("""
                SELECT config_value->>'enabled' FROM app_config
                WHERE config_key = 'ranking.weeklyReward.policy'
                ORDER BY effective_at DESC, id DESC LIMIT 1
                """, String.class)).isEqualTo("false");
        assertThat(jdbcTemplate.queryForObject("""
                SELECT config_value->>'enabled' FROM app_config
                WHERE config_key = 'ranking.systemBoost.policy'
                ORDER BY effective_at DESC, id DESC LIMIT 1
                """, String.class)).isEqualTo("false");
        assertThat(jdbcTemplate.queryForObject("""
                SELECT jsonb_array_length(config_value->'internalUserIds') FROM app_config
                WHERE config_key = 'ranking.systemBoost.policy'
                ORDER BY effective_at DESC, id DESC LIMIT 1
                """, Integer.class)).isEqualTo(6);
    }

    @Test
    void rolloutFailsClosedWhenAnyInternalAccountIsMissing() throws Exception {
        INTERNAL_USER_IDS.stream().limit(5).forEach(this::insertActiveUser);

        assertThatThrownBy(() -> jdbcTemplate.execute(rolloutSql()))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("expected 6 active internal accounts, found 5");
        jdbcTemplate.execute("ROLLBACK");
        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM app_config
                WHERE config_key IN ('ranking.weeklyReward.policy', 'ranking.systemBoost.policy')
                """, Long.class)).isZero();
    }

    private void insertActiveUser(UUID userId) {
        jdbcTemplate.update("""
                INSERT INTO app_user (
                    id, status, nickname, nickname_normalized,
                    onboarding_reward_claimed, created_at, updated_at
                ) VALUES (?, 'ACTIVE', ?, ?, false, now(), now())
                """, userId, "internal-" + userId, "internal-" + userId);
    }

    private String rolloutSql() throws Exception {
        return new ClassPathResource("db/manual-bea-315-308-rollout.sql")
                .getContentAsString(StandardCharsets.UTF_8);
    }
}
