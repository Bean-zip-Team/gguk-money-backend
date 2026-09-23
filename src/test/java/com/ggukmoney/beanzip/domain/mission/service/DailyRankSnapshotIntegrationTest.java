package com.ggukmoney.beanzip.domain.mission.service;

import com.ggukmoney.beanzip.domain.mission.entity.DailyRankSnapshot;
import com.ggukmoney.beanzip.domain.mission.repository.DailyRankSnapshotRepository;
import com.ggukmoney.beanzip.domain.ranking.entity.RankingEntry;
import com.ggukmoney.beanzip.domain.ranking.entity.RankingSeason;
import com.ggukmoney.beanzip.domain.ranking.repository.RankingEntryRepository;
import com.ggukmoney.beanzip.domain.ranking.repository.RankingSeasonRepository;
import com.ggukmoney.beanzip.domain.user.entity.AppUser;
import com.ggukmoney.beanzip.domain.user.repository.AppUserRepository;
import com.ggukmoney.beanzip.support.FullStackIntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 스냅샷을 실제 DB 로 확인한다.
 *
 * <p>순위는 윈도 함수로 매기고 같은 날 다시 돌면 덮어쓴다. 두 동작 모두 SQL 에 있어서 모킹으로는
 * 검증되지 않는다.
 */
// 테스트마다 롤백한다. 주간 시즌 코드가 주 시작일로 유일해서, 남겨 두면 다음 테스트와 부딪힌다.
@Transactional
class DailyRankSnapshotIntegrationTest extends FullStackIntegrationTestSupport {

    private static final Instant NOW = Instant.parse("2025-06-03T14:55:00Z");
    private static final LocalDate SNAPSHOT_DATE = LocalDate.of(2025, 6, 3);

    @Autowired
    private DailyRankSnapshotRepository dailyRankSnapshotRepository;

    @Autowired
    private RankingSeasonRepository rankingSeasonRepository;

    @Autowired
    private RankingEntryRepository rankingEntryRepository;

    @Autowired
    private AppUserRepository appUserRepository;

    @Test
    void recordsEveryParticipantInScoreOrder() {
        RankingSeason season = savedSeason(LocalDate.of(2025, 6, 2));
        AppUser first = savedUserWithScore(season, "first", 1500L);
        AppUser second = savedUserWithScore(season, "second", 900L);
        AppUser third = savedUserWithScore(season, "third", 100L);

        dailyRankSnapshotRepository.capture(season.getId(), SNAPSHOT_DATE, NOW);

        assertThat(rankOf(first)).isEqualTo(1);
        assertThat(rankOf(second)).isEqualTo(2);
        assertThat(rankOf(third)).isEqualTo(3);
    }

    @Test
    void ranksEveryoneExactlyTheWayTheRankingDomainDoes() {
        RankingSeason season = savedSeason(LocalDate.of(2025, 4, 28));
        List<AppUser> participants = List.of(
                savedUserWithScore(season, "top", 900L),
                // 동점 두 명. 여기서 기준이 갈리면 상승폭이 조용히 어긋난다.
                savedUserWithScore(season, "tied-a", 500L),
                savedUserWithScore(season, "tied-b", 500L),
                savedUserWithScore(season, "last", 100L)
        );

        dailyRankSnapshotRepository.capture(season.getId(), SNAPSHOT_DATE, NOW);

        for (AppUser participant : participants) {
            long score = rankingEntryRepository.findMyParticipant(season, participant.getId()).orElseThrow().score();
            long rankingDomainRank =
                    rankingEntryRepository.countParticipantsAhead(season, score, participant.getId().toString()) + 1;
            assertThat(rankOf(participant)).isEqualTo((int) rankingDomainRank);
        }
    }

    @Test
    void recordsWhichSeasonTheSnapshotBelongsTo() {
        RankingSeason season = savedSeason(LocalDate.of(2025, 4, 21));
        AppUser user = savedUserWithScore(season, "seasoned", 700L);

        dailyRankSnapshotRepository.capture(season.getId(), SNAPSHOT_DATE, NOW);

        // 시즌 교체 판정의 유일한 입력값이다. 잘못 들어가면 월요일에 전원이 보상을 가져간다.
        assertThat(dailyRankSnapshotRepository.findByUserIdAndSnapshotDate(user.getId(), SNAPSHOT_DATE))
                .get()
                .extracting(DailyRankSnapshot::getSeasonId)
                .isEqualTo(season.getId());
    }

    @Test
    void leavesOutUsersWhoHaveNotScoredThisWeek() {
        RankingSeason season = savedSeason(LocalDate.of(2025, 5, 26));
        AppUser scored = savedUserWithScore(season, "scored", 500L);
        AppUser silent = savedUserWithScore(season, "silent", 0L);

        dailyRankSnapshotRepository.capture(season.getId(), SNAPSHOT_DATE, NOW);

        // 점수가 0이면 순위 자체가 없다. 스냅샷에 넣으면 다음 날 상승폭이 실제와 달라진다.
        assertThat(dailyRankSnapshotRepository.findByUserIdAndSnapshotDate(scored.getId(), SNAPSHOT_DATE)).isPresent();
        assertThat(dailyRankSnapshotRepository.findByUserIdAndSnapshotDate(silent.getId(), SNAPSHOT_DATE)).isEmpty();
    }

    @Test
    void refreshesTheSnapshotWhenTheBatchRunsAgainOnTheSameDay() {
        RankingSeason season = savedSeason(LocalDate.of(2025, 5, 19));
        AppUser climber = savedUserWithScore(season, "climber", 100L);
        AppUser leader = savedUserWithScore(season, "leader", 900L);
        dailyRankSnapshotRepository.capture(season.getId(), SNAPSHOT_DATE, NOW);
        assertThat(rankOf(climber)).isEqualTo(2);

        RankingEntry climberEntry = rankingEntryRepository.findBySeasonAndUserId(season, climber.getId()).orElseThrow();
        climberEntry.updateScore(2000L, null, NOW);
        rankingEntryRepository.saveAndFlush(climberEntry);
        // 배치가 재시작되거나 수동으로 다시 돌아도 같은 날짜에 행이 두 개 생기지 않는다.
        dailyRankSnapshotRepository.capture(season.getId(), SNAPSHOT_DATE, NOW);

        assertThat(rankOf(climber)).isEqualTo(1);
        assertThat(rankOf(leader)).isEqualTo(2);
    }

    @Test
    void dropsSnapshotsOlderThanTheRetentionWindow() {
        RankingSeason season = savedSeason(LocalDate.of(2025, 5, 12));
        AppUser user = savedUserWithScore(season, "old", 300L);
        dailyRankSnapshotRepository.capture(season.getId(), SNAPSHOT_DATE.minusDays(20), NOW);
        dailyRankSnapshotRepository.capture(season.getId(), SNAPSHOT_DATE, NOW);

        dailyRankSnapshotRepository.deleteOlderThan(SNAPSHOT_DATE.minusDays(14));

        assertThat(dailyRankSnapshotRepository.findByUserIdAndSnapshotDate(user.getId(), SNAPSHOT_DATE.minusDays(20)))
                .isEmpty();
        assertThat(dailyRankSnapshotRepository.findByUserIdAndSnapshotDate(user.getId(), SNAPSHOT_DATE)).isPresent();
    }

    private int rankOf(AppUser user) {
        return dailyRankSnapshotRepository.findByUserIdAndSnapshotDate(user.getId(), SNAPSHOT_DATE)
                .map(DailyRankSnapshot::getRankValue)
                .orElseThrow();
    }

    /** 시즌 코드가 주 시작일로 유일해서, 테스트마다 다른 주를 쓴다. */
    private RankingSeason savedSeason(LocalDate weekStartDate) {
        return rankingSeasonRepository.saveAndFlush(RankingSeason.activeWeekly(
                weekStartDate,
                weekStartDate.minusDays(1).atStartOfDay(java.time.ZoneOffset.UTC).toInstant(),
                weekStartDate.plusDays(6).atStartOfDay(java.time.ZoneOffset.UTC).toInstant()
        ));
    }

    private AppUser savedUserWithScore(RankingSeason season, String nickname, long score) {
        AppUser user = appUserRepository.saveAndFlush(
                AppUser.createActive(nickname + "-" + UUID.randomUUID(), null));
        rankingEntryRepository.saveAndFlush(RankingEntry.createFor(season, user, score, null, NOW));
        return user;
    }
}
