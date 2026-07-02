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
            assertThat(singleLong(statement, "select count(*) from information_schema.tables where table_name = 'auth_session_log'"))
                    .isEqualTo(1L);
            assertColumns(statement);
            assertThat(singleLong(statement, "select count(*) from pg_indexes where tablename = 'auth_session_log' and indexname = 'ix_auth_session_log_user_time'"))
                    .isEqualTo(1L);
            assertThat(singleLong(statement, "select count(*) from pg_constraint where conname = 'ck_auth_session_log_result'"))
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

    private long singleLong(Statement statement, String sql) throws Exception {
        try (ResultSet resultSet = statement.executeQuery(sql)) {
            resultSet.next();
            return resultSet.getLong(1);
        }
    }
}