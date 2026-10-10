package com.ggukmoney.beanzip.domain.keycap.service;
import com.ggukmoney.beanzip.support.FullStackIntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import java.nio.charset.StandardCharsets;
import java.sql.DriverManager;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
class KeycapPassiveMigrationIntegrationTest extends FullStackIntegrationTestSupport {
    @Test void migrationKeepsLegacyBaselinesAndDoesNotOverwriteSubsequentIncrements() throws Exception {
        try(var connection=DriverManager.getConnection(POSTGRES.getJdbcUrl(),POSTGRES.getUsername(),POSTGRES.getPassword());
            var statement=connection.createStatement()) {
            String schema="passive_migration_"+UUID.randomUUID().toString().replace("-","");
            statement.execute("create schema "+schema);
            statement.execute("set search_path to "+schema);
            statement.execute("create table app_user(id uuid primary key)");
            statement.execute("create table user_tap_daily(user_id uuid,total_valid_tap_count integer)");
            statement.execute("create table user_tap_progress(user_id uuid,cumulative_valid_tap_count bigint)");
            statement.execute("create table tap_batch(id bigint)");
            statement.execute("create table ranking_season(id bigint,ranking_type varchar(255))");
            statement.execute("create table ranking_entry(user_id uuid,season_id bigint,score bigint,ranking_boost_score bigint)");
            String user=UUID.randomUUID().toString();
            statement.execute("insert into app_user values ('"+user+"')");
            statement.execute("insert into user_tap_daily values ('"+user+"',4000)");
            statement.execute("insert into user_tap_progress values ('"+user+"',3000)");
            statement.execute("insert into ranking_season values (1,'ALL_TIME')");
            statement.execute("insert into ranking_entry values ('"+user+"',1,5100,100)");
            // A repeatable PG rehearsal; not a production timing estimate.
            statement.execute("insert into app_user select gen_random_uuid() from generate_series(1,20000)");
            statement.execute("insert into user_tap_daily select id,1000 from app_user where id<>'"+user+"'");
            statement.execute("insert into user_tap_progress select id,1000 from app_user where id<>'"+user+"'");
            String preflight=new ClassPathResource("db/manual-bea-349-passive-preflight.sql").getContentAsString(StandardCharsets.UTF_8);
            try(var rows=statement.executeQuery(preflight)) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getLong("daily_rows")).isEqualTo(20001);
                assertThat(rows.getLong("progress_rows")).isEqualTo(20001);
                assertThat(rows.getLong("batch_rows")).isZero();
            }
            String sql=new ClassPathResource("db/manual-bea-349-passive.sql").getContentAsString(StandardCharsets.UTF_8);
            long migrationStart=System.nanoTime();
            statement.execute(sql);
            System.out.printf("BEA-349 migration rehearsal: daily=20001 progress=20001 elapsedMs=%d%n",
                    (System.nanoTime()-migrationStart)/1_000_000);
            try(var rows=statement.executeQuery("select cumulative_mission_tap_count,cumulative_ranking_tap_count from user_tap_progress where user_id='"+user+"'")) {
                assertThat(rows.next()).isTrue(); assertThat(rows.getLong(1)).isEqualTo(3000); assertThat(rows.getLong(2)).isEqualTo(5000);
            }
            statement.execute("update user_tap_daily set total_effective_tap_count=4060");
            statement.execute("update user_tap_progress set cumulative_ranking_tap_count=5060");
            statement.execute(sql);
            try(var rows=statement.executeQuery("select total_valid_tap_count,total_effective_tap_count from user_tap_daily where user_id='"+user+"'")) {
                assertThat(rows.next()).isTrue(); assertThat(rows.getLong(1)).isEqualTo(4000); assertThat(rows.getLong(2)).isEqualTo(4060);
            }
            try(var rows=statement.executeQuery("select cumulative_ranking_tap_count from user_tap_progress where user_id='"+user+"'")) {
                assertThat(rows.next()).isTrue(); assertThat(rows.getLong(1)).isEqualTo(5060);
            }
            statement.execute("drop schema "+schema+" cascade");
        }
    }
}
