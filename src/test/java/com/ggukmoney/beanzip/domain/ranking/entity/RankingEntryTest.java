package com.ggukmoney.beanzip.domain.ranking.entity;

import com.ggukmoney.beanzip.domain.user.entity.AppUser;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RankingEntryTest {

    @Test
    void setsScoreToCurrentCumulativeTapCountInsteadOfIncrementing() {
        AppUser user = AppUser.createActive("ranking-user", null);
        RankingSeason season = RankingSeason.activeAllTime(Instant.parse("2026-07-19T00:00:00Z"));
        RankingEntry entry = RankingEntry.createFor(season, user, 10L, null, Instant.parse("2026-07-19T00:00:01Z"));

        entry.updateScore(25L, null, Instant.parse("2026-07-19T00:00:02Z"));

        assertThat(entry.getScore()).isEqualTo(25L);
    }

    @Test
    void rejectsNegativeScore() {
        AppUser user = AppUser.createActive("ranking-user", null);
        RankingSeason season = RankingSeason.activeAllTime(Instant.parse("2026-07-19T00:00:00Z"));

        assertThatThrownBy(() -> RankingEntry.createFor(season, user, -1L, null, Instant.now()))
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
        RankingSeason season = RankingSeason.activeAllTime(Instant.parse("2026-07-19T00:00:00Z"));

        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(season, "close", (Instant) null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("closedAt");
    }
}
