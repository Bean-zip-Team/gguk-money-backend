package com.ggukmoney.beanzip.persistence;

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
    void migratesMvpSchemaOnEmptyPostgresAndIsRepeatable() throws Exception {
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
            assertThat(singleLong(statement, "select count(*) from flyway_schema_history where version = '1020' and success = true"))
                    .isEqualTo(1L);
            assertMvpTables(statement);
            assertUserIdentityColumns(statement);
            assertJsonbColumns(statement);
            assertConstraints(statement);
            assertIndexes(statement);
            assertUserForeignKeys(statement);
        }
    }

    private void assertMvpTables(Statement statement) throws Exception {
        List<String> tableNames = List.of(
                "app_user",
                "auth_identity",
                "app_config",
                "keycap",
                "user_keycap",
                "keycap_box_account",
                "keycap_box_open",
                "tap_batch",
                "user_tap_daily",
                "point_account",
                "point_ledger",
                "cashout_request",
                "booster_grant");

        for (String tableName : tableNames) {
            assertThat(singleLong(statement, "select count(*) from information_schema.tables where table_name = '" + tableName + "'"))
                    .as("table %s should exist", tableName)
                    .isEqualTo(1L);
        }

        assertThat(singleLong(statement, """
                select count(*)
                from information_schema.tables
                where table_schema = 'public'
                  and table_type = 'BASE TABLE'
                  and table_name not in ('flyway_schema_history')
                """))
                .isEqualTo(13L);
    }

    private void assertUserIdentityColumns(Statement statement) throws Exception {
        assertThat(singleString(statement, "select data_type from information_schema.columns where table_name = 'app_user' and column_name = 'id'"))
                .isEqualTo("uuid");
        assertThat(singleLong(statement, "select count(*) from information_schema.columns where table_name = 'app_user' and column_name = 'public_id'"))
                .isZero();

        try (ResultSet resultSet = statement.executeQuery("""
                select table_name, column_name, data_type
                from information_schema.columns
                where table_schema = 'public' and column_name = 'user_id'
                order by table_name
                """)) {
            List<String> userIdColumns = new ArrayList<>();
            while (resultSet.next()) {
                userIdColumns.add(resultSet.getString("table_name") + "." + resultSet.getString("column_name") + ":" + resultSet.getString("data_type"));
            }
            assertThat(userIdColumns).containsExactly(
                    "auth_identity.user_id:uuid",
                    "booster_grant.user_id:uuid",
                    "cashout_request.user_id:uuid",
                    "keycap_box_account.user_id:uuid",
                    "keycap_box_open.user_id:uuid",
                    "point_account.user_id:uuid",
                    "point_ledger.user_id:uuid",
                    "tap_batch.user_id:uuid",
                    "user_keycap.user_id:uuid",
                    "user_tap_daily.user_id:uuid"
            );
        }
    }

    private void assertJsonbColumns(Statement statement) throws Exception {
        assertThat(singleString(statement, "select data_type from information_schema.columns where table_name = 'app_config' and column_name = 'config_value'"))
                .isEqualTo("jsonb");
        assertThat(singleString(statement, "select data_type from information_schema.columns where table_name = 'tap_batch' and column_name = 'interval_stats'"))
                .isEqualTo("jsonb");
    }

    private void assertConstraints(Statement statement) throws Exception {
        List<String> constraintNames = List.of(
                "ux_auth_identity_provider_user",
                "ux_auth_identity_user_provider",
                "ck_app_user_status",
                "ck_keycap_grade",
                "ck_keycap_box_open_method",
                "ck_tap_batch_status",
                "ck_point_ledger_entry_type",
                "fk_auth_identity_user",
                "fk_user_keycap_user",
                "fk_user_keycap_keycap",
                "fk_point_ledger_account",
                "fk_cashout_request_user");

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
                "ux_keycap_box_open_ad_reward",
                "ux_point_ledger_idempotency_key",
                "ix_cashout_request_status");

        for (String indexName : indexNames) {
            assertThat(singleLong(statement, "select count(*) from pg_indexes where indexname = '" + indexName + "'"))
                    .as("index %s should exist", indexName)
                    .isEqualTo(1L);
        }
    }

    private void assertUserForeignKeys(Statement statement) throws Exception {
        try (ResultSet resultSet = statement.executeQuery("""
                select tc.table_name
                from information_schema.table_constraints tc
                join information_schema.key_column_usage kcu
                  on tc.constraint_name = kcu.constraint_name
                 and tc.table_schema = kcu.table_schema
                join information_schema.constraint_column_usage ccu
                  on ccu.constraint_name = tc.constraint_name
                 and ccu.table_schema = tc.table_schema
                where tc.constraint_type = 'FOREIGN KEY'
                  and tc.table_schema = 'public'
                  and kcu.column_name = 'user_id'
                  and ccu.table_name = 'app_user'
                  and ccu.column_name = 'id'
                order by tc.table_name
                """)) {
            List<String> userFkTables = new java.util.ArrayList<>();
            while (resultSet.next()) {
                userFkTables.add(resultSet.getString("table_name"));
            }
            assertThat(userFkTables).containsExactly(
                    "auth_identity",
                    "booster_grant",
                    "cashout_request",
                    "keycap_box_account",
                    "keycap_box_open",
                    "point_account",
                    "point_ledger",
                    "tap_batch",
                    "user_keycap",
                    "user_tap_daily"
            );
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
