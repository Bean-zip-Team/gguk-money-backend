package com.ggukmoney.beanzip.domain.ranking.repository;

import com.ggukmoney.beanzip.domain.ranking.entity.RankingEntry;
import com.ggukmoney.beanzip.domain.ranking.entity.RankingSeason;
import com.ggukmoney.beanzip.domain.user.entity.AppUser;
import com.ggukmoney.beanzip.domain.user.repository.AppUserRepository;
import com.ggukmoney.beanzip.support.FullStackIntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RankingHistoryRepositoryIntegrationTest extends FullStackIntegrationTestSupport {

    @Autowired
    private RankingEntryRepository entryRepository;

    @Autowired
    private RankingSeasonRepository seasonRepository;

    @Autowired
    private AppUserRepository appUserRepository;

    @Test
    void findsOnlyCompletedClosedWeeklyHistoryForCurrentUserWithCursorTieBreaker() {
        AppUser me = appUserRepository.save(AppUser.createActive("history-me", null));
        AppUser other = appUserRepository.save(AppUser.createActive("history-other", null));
        Instant olderEndsAt = Instant.parse("2026-07-12T15:00:00Z");
        Instant tiedEndsAt = Instant.parse("2026-07-19T15:00:00Z");
        Instant latestEndsAt = Instant.parse("2026-07-26T15:00:00Z");

        RankingSeason older = seasonRepository.save(closedWeekly("WEEKLY_HISTORY_OLDER", olderEndsAt.minusSeconds(604800), olderEndsAt));
        RankingSeason tieLow = seasonRepository.save(closedWeekly("WEEKLY_HISTORY_TIE_LOW", tiedEndsAt.minusSeconds(604800), tiedEndsAt));
        RankingSeason tieHigh = seasonRepository.save(closedWeekly("WEEKLY_HISTORY_TIE_HIGH", tiedEndsAt.minusSeconds(604800), tiedEndsAt));
        RankingSeason latest = seasonRepository.save(closedWeekly("WEEKLY_HISTORY_LATEST", latestEndsAt.minusSeconds(604800), latestEndsAt));
        RankingSeason active = seasonRepository.save(RankingSeason.activeWeekly(
                LocalDate.of(2026, 7, 27),
                Instant.parse("2026-07-26T15:00:00Z"),
                Instant.parse("2026-08-02T15:00:00Z")
        ));
        RankingSeason finalizing = seasonRepository.save(activeWeeklyWithCode(
                "WEEKLY_HISTORY_FINALIZING",
                Instant.parse("2026-07-05T15:00:00Z"),
                Instant.parse("2026-07-12T15:00:00Z")
        ));
        finalizing.startFinalizing();
        RankingSeason allTime = seasonRepository.save(RankingSeason.activeAllTime(Instant.parse("2026-07-01T00:00:00Z")));
        allTime.close(Instant.parse("2026-07-26T15:00:00Z"));

        entryRepository.save(finalizedEntry(older, me, 100L, 3L));
        entryRepository.save(finalizedEntry(tieLow, me, 200L, 2L));
        entryRepository.save(finalizedEntry(tieHigh, me, 300L, 1L));
        entryRepository.save(finalizedEntry(latest, me, 400L, 4L));
        entryRepository.save(finalizedEntry(latest, other, 999L, 1L));
        entryRepository.save(finalizedEntry(active, me, 500L, 1L));
        entryRepository.save(finalizedEntry(finalizing, me, 600L, 1L));
        RankingEntry allTimeEntry = RankingEntry.createFor(
                allTime,
                me,
                700L,
                null,
                Instant.parse("2026-07-26T15:00:00Z")
        );
        allTimeEntry.finalizeRank(1L, Instant.parse("2026-07-26T15:10:00Z"));
        entryRepository.save(allTimeEntry);
        entryRepository.save(RankingEntry.createFor(
                seasonRepository.save(closedWeekly("WEEKLY_HISTORY_INCOMPLETE_RANK", Instant.parse("2026-06-22T15:00:00Z"), Instant.parse("2026-06-29T15:00:00Z"))),
                me,
                800L,
                null,
                Instant.parse("2026-06-29T15:00:00Z")
        ));
        RankingEntry missingFinalizedAt = entryRepository.saveAndFlush(finalizedEntry(
                seasonRepository.save(closedWeekly("WEEKLY_HISTORY_INCOMPLETE_FINALIZED", Instant.parse("2026-06-15T15:00:00Z"), Instant.parse("2026-06-22T15:00:00Z"))),
                me,
                900L,
                9L
        ));
        jdbcTemplate.update("UPDATE ranking_entry SET finalized_at = NULL WHERE id = ?", missingFinalizedAt.getId());

        List<RankingEntryRepository.RankingHistoryRow> firstPage =
                entryRepository.findWeeklyHistory(me.getId(), null, null, 3);
        List<RankingEntryRepository.RankingHistoryRow> secondPage =
                entryRepository.findWeeklyHistory(me.getId(), tiedEndsAt, tieHigh.getId(), 3);

        assertThat(firstPage)
                .extracting(RankingEntryRepository.RankingHistoryRow::seasonCode)
                .containsExactly("WEEKLY_HISTORY_LATEST", "WEEKLY_HISTORY_TIE_HIGH", "WEEKLY_HISTORY_TIE_LOW");
        assertThat(firstPage.get(0).finalScore()).isEqualTo(400L);
        assertThat(firstPage.get(0).finalRank()).isEqualTo(4L);
        assertThat(secondPage)
                .extracting(RankingEntryRepository.RankingHistoryRow::seasonCode)
                .containsExactly("WEEKLY_HISTORY_TIE_LOW", "WEEKLY_HISTORY_OLDER");
        assertThat(firstPage).extracting(RankingEntryRepository.RankingHistoryRow::seasonCode)
                .doesNotContain("WEEKLY_HISTORY_FINALIZING", "ALL_TIME", "WEEKLY_HISTORY_INCOMPLETE_RANK", "WEEKLY_HISTORY_INCOMPLETE_FINALIZED");
    }

    private RankingSeason closedWeekly(String code, Instant startsAt, Instant endsAt) {
        RankingSeason season = activeWeeklyWithCode(code, startsAt, endsAt);
        season.startFinalizing();
        season.close(endsAt.plusSeconds(600));
        return season;
    }

    private RankingSeason activeWeeklyWithCode(String code, Instant startsAt, Instant endsAt) {
        RankingSeason season = RankingSeason.activeWeekly(LocalDate.ofInstant(startsAt, java.time.ZoneOffset.UTC), startsAt, endsAt);
        ReflectionTestUtils.setField(season, "code", code);
        return season;
    }

    private RankingEntry finalizedEntry(RankingSeason season, AppUser user, long score, long finalRank) {
        RankingEntry entry = RankingEntry.createFor(season, user, score, null, season.getEndsAt());
        entry.finalizeRank(finalRank, season.getEndsAt().plusSeconds(600));
        return entry;
    }
}
