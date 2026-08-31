package com.ggukmoney.beanzip.domain.tap.repository;

import com.ggukmoney.beanzip.domain.tap.entity.UserTapDaily;
import com.ggukmoney.beanzip.domain.user.entity.AppUser;
import com.ggukmoney.beanzip.domain.user.repository.AppUserRepository;
import com.ggukmoney.beanzip.support.FullStackIntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Transactional
class UserTapDailyRepositoryIntegrationTest extends FullStackIntegrationTestSupport {

    @Autowired
    private UserTapDailyRepository dailyRepository;

    @Autowired
    private AppUserRepository appUserRepository;

    @Test
    void nativeUuidKeysetConnectsMultiplePagesWithoutMissingOrDuplicateUsers() {
        LocalDate startDate = LocalDate.parse("2026-07-20");
        LocalDate endDate = LocalDate.parse("2026-07-27");
        Map<UUID, Long> expectedScores = new LinkedHashMap<>();
        for (int index = 1; index <= 5; index++) {
            AppUser user = appUserRepository.save(AppUser.createActive("uuid-page-" + index, null));
            dailyRepository.save(daily(user, startDate.plusDays(1), index));
            dailyRepository.save(daily(user, startDate.plusDays(2), 10));
            expectedScores.put(user.getId(), 10L + index);
        }
        AppUser outside = appUserRepository.save(AppUser.createActive("uuid-page-outside", null));
        dailyRepository.save(daily(outside, startDate.minusDays(1), 999));
        AppUser zero = appUserRepository.save(AppUser.createActive("uuid-page-zero", null));
        dailyRepository.save(UserTapDaily.createFor(zero, startDate.plusDays(1)));
        dailyRepository.flush();

        List<UserTapDailyRepository.UserTapAggregateProjection> allRows = new ArrayList<>();
        UUID cursor = null;
        while (true) {
            List<UserTapDailyRepository.UserTapAggregateProjection> page =
                    dailyRepository.findTotalValidTapAggregates(startDate, endDate, cursor, 2);
            if (page.isEmpty()) {
                break;
            }
            allRows.addAll(page);
            cursor = page.getLast().getUserId();
            if (page.size() < 2) {
                break;
            }
        }

        List<UUID> expectedOrder = expectedScores.keySet().stream()
                .sorted(Comparator.comparing(UUID::toString))
                .toList();
        assertThat(allRows).extracting(UserTapDailyRepository.UserTapAggregateProjection::getUserId)
                .containsExactlyElementsOf(expectedOrder)
                .doesNotHaveDuplicates();
        assertThat(allRows).allSatisfy(row -> assertThat(row.getScore())
                .isEqualTo(expectedScores.get(row.getUserId())));
    }

    @Test
    void capturesCastNativeAndIndexedNativePlans() {
        String castExplain = """
                EXPLAIN (ANALYZE, BUFFERS, VERBOSE)
                SELECT daily.user_id, COALESCE(SUM(daily.total_valid_tap_count), 0)
                FROM user_tap_daily daily
                WHERE daily.tap_date >= '2026-07-20'
                  AND daily.tap_date < '2026-07-27'
                  AND daily.total_valid_tap_count > 0
                  AND CAST(daily.user_id AS text) > '00000000-0000-0000-0000-000000000000'
                GROUP BY daily.user_id
                ORDER BY CAST(daily.user_id AS text) ASC
                LIMIT 500
                """;
        String nativeExplain = """
                EXPLAIN (ANALYZE, BUFFERS, VERBOSE)
                SELECT daily.user_id, COALESCE(SUM(daily.total_valid_tap_count), 0)
                FROM user_tap_daily daily
                WHERE daily.tap_date >= '2026-07-20'
                  AND daily.tap_date < '2026-07-27'
                  AND daily.total_valid_tap_count > 0
                  AND daily.user_id > '00000000-0000-0000-0000-000000000000'::uuid
                GROUP BY daily.user_id
                ORDER BY daily.user_id ASC
                LIMIT 500
                """;
        jdbcTemplate.execute("DROP INDEX ix_user_tap_daily_date_user");
        List<String> castPlan = jdbcTemplate.queryForList(castExplain, String.class);
        List<String> nativePlan = jdbcTemplate.queryForList(nativeExplain, String.class);
        jdbcTemplate.execute("""
                CREATE INDEX ix_user_tap_daily_date_user
                ON user_tap_daily (tap_date, user_id)
                """);
        jdbcTemplate.execute("ANALYZE user_tap_daily");
        List<String> indexedNativePlan = jdbcTemplate.queryForList(nativeExplain, String.class);

        System.out.printf("BEA-254 cast plan:%n%s%nBEA-254 native plan:%n%s%nBEA-254 indexed native plan:%n%s%n",
                String.join(System.lineSeparator(), castPlan),
                String.join(System.lineSeparator(), nativePlan),
                String.join(System.lineSeparator(), indexedNativePlan));
        assertThat(castPlan).anyMatch(line -> line.contains("user_tap_daily"));
        assertThat(nativePlan).anyMatch(line -> line.contains("user_tap_daily"));
        assertThat(indexedNativePlan).anyMatch(line -> line.contains("user_tap_daily"));
    }

    private UserTapDaily daily(AppUser user, LocalDate date, int totalValidTapCount) {
        UserTapDaily daily = UserTapDaily.createFor(user, date);
        daily.addTotalValidTaps(totalValidTapCount);
        return daily;
    }
}
