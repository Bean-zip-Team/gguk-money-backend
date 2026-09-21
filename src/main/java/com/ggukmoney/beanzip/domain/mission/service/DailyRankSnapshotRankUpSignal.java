package com.ggukmoney.beanzip.domain.mission.service;

import com.ggukmoney.beanzip.domain.mission.entity.DailyRankSnapshot;
import com.ggukmoney.beanzip.domain.mission.repository.DailyRankSnapshotRepository;
import com.ggukmoney.beanzip.domain.ranking.entity.RankingSeason;
import com.ggukmoney.beanzip.domain.ranking.repository.RankingEntryRepository;
import com.ggukmoney.beanzip.domain.ranking.service.RankingSeasonService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * 어제 스냅샷과 현재 순위를 비교해 상승폭을 낸다 (BEA-299).
 *
 * <p>다음 세 경우에는 <b>판정하지 않는다</b>. 미션이 목록에서 빠진다.
 *
 * <ul>
 *   <li>주간 시즌이 없을 때</li>
 *   <li>어제 스냅샷이 없을 때 — 오늘 처음 참가한 유저가 여기 해당한다</li>
 *   <li>어제 스냅샷이 다른 시즌의 것일 때 — <b>월요일</b>이다. 시즌이 초기화되어 전원이 0점에서
 *       다시 시작하므로, 비교하면 모두가 큰 폭으로 오른 것처럼 잡힌다</li>
 * </ul>
 *
 * <p>요일을 코드에 박지 않고 시즌으로 판정한다. 리셋 요일이 바뀌어도 이 규칙은 그대로 맞는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DailyRankSnapshotRankUpSignal implements RankUpSignal {

    private final DailyRankSnapshotRepository dailyRankSnapshotRepository;
    private final RankingSeasonService rankingSeasonService;
    private final RankingEntryRepository rankingEntryRepository;

    @Override
    @Transactional(readOnly = true)
    public Optional<Long> rankUpOf(UUID userId, LocalDate today) {
        try {
            return calculateRankUp(userId, today);
        } catch (RuntimeException exception) {
            // 랭킹 쪽이 흔들렸다고 미션 목록과 보상 수령까지 500 으로 떨어뜨리지 않는다.
            // 랭킹 미션만 오늘 목록에서 빠지고 나머지는 그대로 동작한다.
            log.warn("RANK_UP_SIGNAL_FAILED userId={} today={}", userId, today, exception);
            return Optional.empty();
        }
    }

    private Optional<Long> calculateRankUp(UUID userId, LocalDate today) {
        Optional<RankingSeason> season = rankingSeasonService.findActiveWeeklySeason();
        if (season.isEmpty()) {
            return Optional.empty();
        }

        Optional<DailyRankSnapshot> yesterday =
                dailyRankSnapshotRepository.findByUserIdAndSnapshotDate(userId, today.minusDays(1));
        if (yesterday.isEmpty() || !Objects.equals(yesterday.get().getSeasonId(), season.get().getId())) {
            return Optional.empty();
        }

        return currentRankOf(season.get(), userId)
                // 순위가 내려갔으면 0이다. 음수를 돌려주면 진행도 표시가 이상해진다.
                .map(currentRank -> Math.max(0L, yesterday.get().getRankValue() - currentRank));
    }

    /**
     * 지금 순위.
     *
     * <p>랭킹 화면이 Redis 없이 순위를 구할 때 쓰는 경로와 같다 — 참가자 전체에 순번을 매기는 대신
     * "나보다 앞선 사람 수 + 1" 을 센다. 미션 목록은 조회마다 호출되므로 시즌 전체를 훑으면 안 된다.
     *
     * <p>이번 주에 한 번도 누르지 않아 순위가 없으면 비어 있다. 비교할 값이 없으므로 미션도 판정하지 않는다.
     */
    private Optional<Long> currentRankOf(RankingSeason season, UUID userId) {
        return rankingEntryRepository.findMyParticipant(season, userId)
                .map(participant -> rankingEntryRepository.countParticipantsAhead(
                        season, participant.score(), userId.toString()) + 1);
    }
}
