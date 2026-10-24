package com.ggukmoney.beanzip.domain.tap.repository;

import com.ggukmoney.beanzip.support.FullStackIntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TapBatchClickCriticalMigrationIntegrationTest extends FullStackIntegrationTestSupport {
    @Test
    void manualMigrationAddsNullableAuditColumnsWithTheEntityTypesAndIsRepeatable() throws Exception {
        // This isolated Testcontainers database emulates the pre-BEA-367 schema.
        jdbcTemplate.execute("ALTER TABLE tap_batch DROP COLUMN start_tap_number, DROP COLUMN equipped_keycap_code, "
                + "DROP COLUMN click_critical_probability, DROP COLUMN click_critical_multiplier, DROP COLUMN click_critical_count");
        String migration = new ClassPathResource("db/manual-bea-367-tap-batch-click-critical.sql").getContentAsString(StandardCharsets.UTF_8);
        jdbcTemplate.execute(migration);
        jdbcTemplate.execute(migration);
        var types = jdbcTemplate.queryForList("SELECT column_name, data_type, is_nullable FROM information_schema.columns "
                + "WHERE table_name='tap_batch' AND column_name IN ('start_tap_number','equipped_keycap_code',"
                + "'click_critical_probability','click_critical_multiplier','click_critical_count') ORDER BY column_name");
        assertThat(types).containsExactly(
                Map.of("column_name", "click_critical_count", "data_type", "integer", "is_nullable", "YES"),
                Map.of("column_name", "click_critical_multiplier", "data_type", "integer", "is_nullable", "YES"),
                Map.of("column_name", "click_critical_probability", "data_type", "double precision", "is_nullable", "YES"),
                Map.of("column_name", "equipped_keycap_code", "data_type", "character varying", "is_nullable", "YES"),
                Map.of("column_name", "start_tap_number", "data_type", "bigint", "is_nullable", "YES"));
    }
}
