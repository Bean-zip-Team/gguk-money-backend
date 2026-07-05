package com.ggukmoney.beanzip.domain.auth.audit;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class FlywayMigrationIntegrationTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

    @Test
    void migratesAuthSessionLogSchemaOnEmptyPostgresAndIsRepeatable() throws Exception {
        Flyway flyway = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .load();

        flyway.migrate();
        flyway.migrate();

        try (Connection connection = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             Statement statement = connection.createStatement()) {
            assertThat(singleLong(statement, "select count(*) from flyway_schema_history where version = '1000' and success = true"))
                    .isEqualTo(1L);
            assertThat(singleLong(statement, "select count(*) from flyway_schema_history where version = '1010' and success = true"))
                    .isEqualTo(1L);
            assertThat(singleLong(statement, "select count(*) from information_schema.tables where table_name = 'auth_session_log'"))
                    .isEqualTo(1L);
            assertTables(statement);
            assertColumns(statement);
            assertJsonbColumns(statement);
            assertThat(singleLong(statement, "select count(*) from pg_indexes where tablename = 'auth_session_log' and indexname = 'ix_auth_session_log_user_time'"))
                    .isEqualTo(1L);
            assertThat(singleLong(statement, "select count(*) from pg_constraint where conname = 'ck_auth_session_log_result'"))
                    .isEqualTo(1L);
            assertConstraints(statement);
            assertIndexes(statement);
        }
    }

    private void assertTables(Statement statement) throws Exception {
        List<String> tableNames = List.of(
                "app_user",
                "auth_identity",
                "legal_document",
                "user_consent",
                "app_config",
                "keycap",
                "user_keycap",
                "keycap_drop_table",
                "keycap_drop_item",
                "keycap_box_account",
                "keycap_box_ledger",
                "keycap_box_open",
                "keycap_box_open_result",
                "region",
                "user_region",
                "user_region_change",
                "ranking_season",
                "ranking_participation",
                "ranking_score",
                "ranking_score_event",
                "ranking_snapshot",
                "ranking_reward",
                "push_device",
                "notification_preference",
                "notification_log",
                "user_record_daily",
                "user_record_summary",
                "user_record_reward",
                "event_outbox",
                "event_inbox");

        for (String tableName : tableNames) {
            assertThat(singleLong(statement, "select count(*) from information_schema.tables where table_name = '" + tableName + "'"))
                    .as("table %s should exist", tableName)
                    .isEqualTo(1L);
        }
    }

    private void assertColumns(Statement statement) throws Exception {
        try (ResultSet resultSet = statement.executeQuery("""
                select column_name, data_type, is_nullable, is_identity
                from information_schema.columns
                where table_name = 'auth_session_log'
                order by ordinal_position
                """)) {
            List<String> columns = new ArrayList<>();
            while (resultSet.next()) {
                columns.add(resultSet.getString("column_name") + ":" + resultSet.getString("data_type") + ":" + resultSet.getString("is_nullable") + ":" + resultSet.getString("is_identity"));
            }
            assertThat(columns).contains(
                    "id:bigint:NO:YES",
                    "public_id:uuid:NO:NO",
                    "user_public_id:uuid:YES:NO",
                    "device_public_id:uuid:YES:NO",
                    "session_id_hash:character varying:YES:NO",
                    "token_family_id_hash:character varying:YES:NO",
                    "event_type:character varying:NO:NO",
                    "result:character varying:NO:NO",
                    "failure_code:character varying:YES:NO",
                    "trace_id:character varying:YES:NO",
                    "ip_address_masked:character varying:YES:NO",
                    "user_agent:character varying:YES:NO",
                    "metadata:jsonb:YES:NO",
                    "occurred_at:timestamp with time zone:NO:NO",
                    "created_at:timestamp with time zone:NO:NO",
                    "updated_at:timestamp with time zone:NO:NO"
            );
        }
    }

    private void assertJsonbColumns(Statement statement) throws Exception {
        assertThat(singleString(statement, "select data_type from information_schema.columns where table_name = 'app_config' and column_name = 'config_value'"))
                .isEqualTo("jsonb");
        assertThat(singleString(statement, "select data_type from information_schema.columns where table_name = 'event_outbox' and column_name = 'payload'"))
                .isEqualTo("jsonb");
        assertThat(singleString(statement, "select data_type from information_schema.columns where table_name = 'event_inbox' and column_name = 'payload'"))
                .isEqualTo("jsonb");
    }

    private void assertConstraints(Statement statement) throws Exception {
        List<String> constraintNames = List.of(
                "ux_auth_identity_provider_user",
                "ux_auth_identity_user_provider",
                "ck_app_user_status",
                "ck_keycap_grade",
                "ck_keycap_drop_item_grant_mode",
                "ck_keycap_box_open_method",
                "ck_ranking_season_status",
                "ck_notification_log_status",
                "ck_event_outbox_status",
                "fk_auth_identity_user",
                "fk_user_keycap_user",
                "fk_user_keycap_keycap",
                "fk_ranking_participation_season",
                "fk_ranking_participation_user",
                "fk_notification_log_push_device");

        for (String constraintName : constraintNames) {
            assertThat(singleLong(statement, "select count(*) from pg_constraint where conname = '" + constraintName + "'"))
                    .as("constraint %s should exist", constraintName)
                    .isEqualTo(1L);
        }
    }

    private void assertIndexes(Statement statement) throws Exception {
        List<String> indexNames = List.of(
                "ux_app_user_active_nickname_normalized",
                "ux_user_keycap_equipped",
                "ux_keycap_box_open_ad_view",
                "ux_user_region_change_scheduled",
                "ix_event_outbox_status_created",
                "ix_event_inbox_processed");

        for (String indexName : indexNames) {
            assertThat(singleLong(statement, "select count(*) from pg_indexes where indexname = '" + indexName + "'"))
                    .as("index %s should exist", indexName)
                    .isEqualTo(1L);
        }
    }

    private long singleLong(Statement statement, String sql) throws Exception {
        try (ResultSet resultSet = statement.executeQuery(sql)) {
            resultSet.next();
            return resultSet.getLong(1);
        }
    }

    private String singleString(Statement statement, String sql) throws Exception {
        try (ResultSet resultSet = statement.executeQuery(sql)) {
            resultSet.next();
            return resultSet.getString(1);
        }
    }
}
