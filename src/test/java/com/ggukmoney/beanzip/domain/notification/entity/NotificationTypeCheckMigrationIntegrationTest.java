package com.ggukmoney.beanzip.domain.notification.entity;

import com.ggukmoney.beanzip.support.FullStackIntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 운영 DB 의 알림 테이블에는 예전에 Hibernate 가 만든 {@code notification_type} CHECK 제약이 남아 있다.
 * 스키마는 수동 SQL 로 관리하므로, 타입을 추가하고 이 SQL 을 갱신하지 않으면 운영에서만 INSERT 가 실패한다.
 * DAILY_MISSION 이 빠져서 동의 저장이 500 을 냈다(BEA-299).
 */
class NotificationTypeCheckMigrationIntegrationTest extends FullStackIntegrationTestSupport {

    @Test
    void allowsEveryNotificationTypeOnBothTables() throws Exception {
        jdbcTemplate.execute(new ClassPathResource("db/manual-notification-type-check.sql")
                .getContentAsString(StandardCharsets.UTF_8));

        for (String table : new String[]{"notification_preference", "notification_delivery"}) {
            String definition = jdbcTemplate.queryForObject("""
                    SELECT pg_get_constraintdef(oid) FROM pg_constraint
                    WHERE conrelid = ?::regclass AND conname = ? || '_notification_type_check'
                    """, String.class, table, table);
            assertThat(definition).as(table).contains(
                    Arrays.stream(NotificationType.values()).map(type -> "'" + type.name() + "'").toList());
        }
    }
}
