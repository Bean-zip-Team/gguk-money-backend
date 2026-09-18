package com.ggukmoney.beanzip.domain.ranking.entity;

import com.ggukmoney.beanzip.domain.user.entity.AppUser;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RankingEntryTest {

    private static final Instant NOW = Instant.parse("2026-07-19T00:00:00Z");

    @Test
    void rankingOnlyBoostSurvivesRealTapUpdatesAndZeroRealTapRecount() {
        RankingSeason season = RankingSeason.activeWeekly(java.time.LocalDate.of(2026, 7, 13),
                NOW.minusSeconds(86400), NOW.plusSeconds(86400));
        RankingEntry entry = RankingEntry.createFor(season, AppUser.createActive("staff", null), 100L, null, NOW);
        ReflectionTestUtils.invokeMethod(entry, "boostTo", 1300L, NOW);
        assertThat(entry.getScore()).isEqualTo(1300L);
        entry.updateScore(150L, null, NOW);
        assertThat(entry.getScore()).isEqualTo(1350L);
        entry.updateScore(0L, null, NOW);
        assertThat(entry.getScore()).isEqualTo(1200L);
    }

    @Test
    void rejectsOverflowWhenRealScoreAndBoostAreCombined() {
        RankingSeason season = RankingSeason.activeWeekly(java.time.LocalDate.of(2026, 7, 13),
                NOW.minusSeconds(86400), NOW.plusSeconds(86400));
        RankingEntry entry = RankingEntry.createFor(season, AppUser.createActive("staff", null), 0L, null, NOW);
        ReflectionTestUtils.invokeMethod(entry, "boostTo", 100L, NOW);
        assertThatThrownBy(() -> entry.updateScore(Long.MAX_VALUE, null, NOW))
                .isInstanceOf(ArithmeticException.class);
        assertThat(entry.getScore()).isEqualTo(100L);
    }

    @Test
    void boostRejectsAllTimeFinalizingExpiredAndNonIncreasingTargets() {
        AppUser user = AppUser.createActive("staff", null);
        RankingEntry allTime = RankingEntry.createFor(RankingSeason.activeAllTime(NOW), user, 100L, null, NOW);
        assertThatThrownBy(() -> allTime.boostTo(200L, NOW)).isInstanceOf(IllegalStateException.class);
        RankingSeason weekly = RankingSeason.activeWeekly(java.time.LocalDate.of(2026, 7, 13), NOW.minusSeconds(86400), NOW.plusSeconds(86400));
        RankingEntry entry = RankingEntry.createFor(weekly, user, 100L, null, NOW);
        assertThatThrownBy(() -> entry.boostTo(100L, NOW)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> entry.boostTo(200L, weekly.getEndsAt())).isInstanceOf(IllegalStateException.class);
        weekly.startFinalizing();
        assertThatThrownBy(() -> entry.boostTo(200L, NOW)).isInstanceOf(IllegalStateException.class);
        assertThat(entry.getScore()).isEqualTo(100L);
        assertThat(entry.getRankingBoostScore()).isZero();
    }

    @Test
    void setsScoreToCurrentCumulativeTapCountInsteadOfIncrementing() {
        AppUser user = AppUser.createActive("ranking-user", null);
        RankingSeason season = RankingSeason.activeAllTime(NOW);
        RankingEntry entry = RankingEntry.createFor(season, user, 10L, null, Instant.parse("2026-07-19T00:00:01Z"));

        entry.updateScore(25L, null, Instant.parse("2026-07-19T00:00:02Z"));

        assertThat(entry.getScore()).isEqualTo(25L);
    }

    @Test
    void rejectsNegativeScore() {
        AppUser user = AppUser.createActive("ranking-user", null);
        RankingSeason season = RankingSeason.activeAllTime(NOW);

        assertThatThrownBy(() -> RankingEntry.createFor(season, user, -1L, null, NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("score");
    }

    @Test
    void activeAllTimeSeasonUsesAllTimeCodeAndNullEndTime() {
        Instant startsAt = Instant.parse("2026-07-19T00:00:00Z");

        RankingSeason season = RankingSeason.activeAllTime(startsAt);

        assertThat(season.getCode()).isEqualTo("ALL_TIME");
        assertThat(season.getRankingType()).isEqualTo(RankingType.ALL_TIME);
        assertThat(season.getStatus()).isEqualTo(RankingSeasonStatus.ACTIVE);
        assertThat(season.getStartsAt()).isEqualTo(startsAt);
        assertThat(season.getEndsAt()).isNull();
    }

    @Test
    void closedSeasonRequiresClosedAt() {
        RankingSeason season = RankingSeason.activeAllTime(NOW);

        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(season, "close", (Instant) null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("closedAt");
    }

    @Test
    void weeklySeasonMustStartFinalizingBeforeClose() {
        RankingSeason season = RankingSeason.activeWeekly(
                java.time.LocalDate.of(2026, 7, 20),
                Instant.parse("2026-07-19T15:00:00Z"),
                Instant.parse("2026-07-26T15:00:00Z")
        );

        assertThatThrownBy(() -> season.close(NOW))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("finalizing");

        season.startFinalizing();
        season.close(NOW);

        assertThat(season.getStatus()).isEqualTo(RankingSeasonStatus.CLOSED);
        assertThat(season.getClosedAt()).isEqualTo(NOW);
    }

    @Test
    void finalRankSnapshotRequiresPositiveRankAndFinalizedAt() {
        AppUser user = AppUser.createActive("ranking-user", null);
        RankingSeason season = RankingSeason.activeAllTime(NOW);
        RankingEntry entry = RankingEntry.createFor(season, user, 10L, null, NOW);

        assertThatThrownBy(() -> entry.finalizeRank(0, NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("positive");
        assertThatThrownBy(() -> entry.finalizeRank(1, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("finalizedAt");

        entry.finalizeRank(1, NOW);

        assertThat(entry.getFinalRank()).isEqualTo(1L);
        assertThat(entry.getFinalizedAt()).isEqualTo(NOW);
        assertThat(entry.hasFinalRankSnapshot()).isTrue();
    }

    @Test
    void finalRankSnapshotCannotBeOverwrittenWithDifferentValue() {
        AppUser user = AppUser.createActive("ranking-user", null);
        RankingSeason season = RankingSeason.activeAllTime(NOW);
        RankingEntry entry = RankingEntry.createFor(season, user, 10L, null, NOW);
        entry.finalizeRank(1, NOW);

        assertThatThrownBy(() -> entry.finalizeRank(2, NOW))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("overwritten");
    }
}
