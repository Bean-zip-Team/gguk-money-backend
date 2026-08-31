package com.ggukmoney.beanzip.global.config.repository;

import com.ggukmoney.beanzip.global.config.entity.AppConfig;
import com.ggukmoney.beanzip.support.FullStackIntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

@Transactional
class AppConfigRepositoryTest extends FullStackIntegrationTestSupport {

    @Autowired
    private AppConfigRepository repository;

    @Test
    void findsLatestEffectiveRowPerRequestedKeyAndExcludesFutureRows() {
        Instant now = Instant.parse("2026-08-31T00:00:00Z");
        repository.saveAll(List.of(
                AppConfig.createFor("tap.a", "1", now.minusSeconds(20)),
                AppConfig.createFor("tap.a", "2", now.minusSeconds(10)),
                AppConfig.createFor("tap.a", "3", now.plusSeconds(10)),
                AppConfig.createFor("tap.b", "4", now.minusSeconds(5)),
                AppConfig.createFor("ignored", "5", now.minusSeconds(1))
        ));
        repository.flush();

        List<AppConfig> rows = repository.findLatestEffectiveByConfigKeys(Set.of("tap.a", "tap.b"), now);

        assertThat(rows).extracting(AppConfig::getConfigKey, AppConfig::getConfigValue)
                .containsExactly(tuple("tap.a", "2"), tuple("tap.b", "4"));
    }

    @Test
    void breaksSameEffectiveTimeTieByHighestId() {
        Instant effectiveAt = Instant.parse("2026-08-31T00:00:00Z");
        jdbcTemplate.execute("ALTER TABLE app_config DROP CONSTRAINT uq_app_config_key_effective");
        Long firstId = insertDirectly("tap.tie", "1", effectiveAt);
        Long secondId = insertDirectly("tap.tie", "2", effectiveAt);

        List<AppConfig> rows = repository.findLatestEffectiveByConfigKeys(Set.of("tap.tie"), effectiveAt);

        assertThat(secondId).isGreaterThan(firstId);
        assertThat(rows).singleElement().satisfies(row -> {
            assertThat(row.getId()).isEqualTo(secondId);
            assertThat(row.getConfigValue()).isEqualTo("2");
        });
    }

    private Long insertDirectly(String key, String value, Instant effectiveAt) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO app_config (public_id, config_key, config_value, effective_at, created_at, updated_at)
                VALUES (?, ?, CAST(? AS jsonb), ?, ?, ?)
                RETURNING id
                """, Long.class, UUID.randomUUID(), key, value,
                Timestamp.from(effectiveAt), Timestamp.from(effectiveAt), Timestamp.from(effectiveAt));
    }
}
