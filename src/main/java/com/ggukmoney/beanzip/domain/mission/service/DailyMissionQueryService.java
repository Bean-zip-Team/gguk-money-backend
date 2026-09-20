package com.ggukmoney.beanzip.domain.mission.service;

import com.ggukmoney.beanzip.domain.mission.entity.MissionDefinition;
import com.ggukmoney.beanzip.domain.promotion.dto.response.MissionListResponse;
import com.ggukmoney.beanzip.domain.tap.entity.UserTapDaily;
import com.ggukmoney.beanzip.domain.tap.repository.UserTapDailyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 오늘의 데일리 미션 목록과 진행도 (BEA-299).
 *
 * <p>진행도는 저장하지 않고 조회 시점에 계산한다. 탭 수·출석·알림 동의는 이미 다른 테이블이 들고
 * 있어 따로 적재하면 같은 사실이 두 군데 남는다.
 */
@Service
@RequiredArgsConstructor
public class DailyMissionQueryService {

    /** 연속 출석을 세려고 읽는 최근 일자 수. 60일이면 화면에 쓰기 충분하고 쿼리도 가볍다. */
    private static final int ATTENDANCE_LOOKBACK_DAYS = 60;

    private final MissionDefinitionCatalog missionDefinitionCatalog;
    private final UserTapDailyRepository userTapDailyRepository;
    private final RankUpSignal rankUpSignal;
    private final NotificationOptInSignal notificationOptInSignal;
    private final ZoneId businessZoneId;
    private final Clock clock;

    @Transactional(readOnly = true)
    public DailyMissionFeed feedOf(UUID userId) {
        Instant now = clock.instant();
        LocalDate today = LocalDate.ofInstant(now, businessZoneId);

        Optional<UserTapDaily> todayTapDaily = userTapDailyRepository.findByUserIdAndTapDate(userId, today);
        long todayTapCount = todayTapDaily.map(daily -> (long) daily.getTotalValidTapCount()).orElse(0L);
        Optional<Long> rankUp = rankUpSignal.rankUpOf(userId, today);
        boolean notificationAgreed = notificationOptInSignal.agreed(userId);

        List<MissionListResponse.Mission> missions = new ArrayList<>();
        int dailyShownCount = 0;
        int completedCount = 0;
        long achievedRewardTotal = 0L;

        for (MissionDefinitionView definition : missionDefinitionCatalog.activeDefinitions()) {
            Optional<Long> current =
                    currentValueOf(definition, todayTapDaily.isPresent(), todayTapCount, rankUp, notificationAgreed);
            if (current.isEmpty()) {
                // 오늘 판정할 수 없는 미션은 목록에서 뺀다. 달성할 수 없는 미션을 0/5 로 계속 보여주면
                // 유저는 눌러도 반응이 없는 줄 안다. 월요일의 랭킹 미션이 여기 해당한다.
                continue;
            }

            boolean achieved = current.get() >= definition.targetValue();
            missions.add(toMission(definition, current.get(), achieved));

            // 요약은 "오늘의 미션" 진행도 게이지가 쓰는 값이라 매일 반복되는 미션만 센다. 단발성
            // 미션까지 넣으면 한 번 받은 알림 허용 보상이 매일 받을 수 있는 것처럼 합계에 남는다.
            if (definition.periodType() != MissionDefinition.PeriodType.DAILY) {
                continue;
            }
            dailyShownCount++;
            if (achieved) {
                completedCount++;
                achievedRewardTotal += definition.rewardPointAmount();
            }
        }

        return new DailyMissionFeed(
                missions,
                new MissionListResponse.DailySummary(
                        completedCount,
                        dailyShownCount,
                        achievedRewardTotal,
                        today.plusDays(1).atStartOfDay(businessZoneId).toInstant(),
                        consecutiveAttendanceDays(userId, today)
                )
        );
    }

    /**
     * 미션 종류별 현재 값. 비어 있으면 <b>오늘은 판정할 수 없다</b>는 뜻이다.
     */
    private Optional<Long> currentValueOf(
            MissionDefinitionView definition,
            boolean attendedToday,
            long todayTapCount,
            Optional<Long> rankUp,
            boolean notificationAgreed
    ) {
        return switch (definition.missionType()) {
            // 앱을 켜면 홈이 오늘 탭 상태를 조회하면서 오늘자 행을 만든다. 그래서 행의 존재가 곧 출석이다.
            case ATTENDANCE -> Optional.of(attendedToday ? 1L : 0L);
            case TAP_COUNT -> Optional.of(todayTapCount);
            case RANK_UP -> rankUp;
            case NOTIFICATION_OPT_IN -> Optional.of(notificationAgreed ? 1L : 0L);
        };
    }

    private MissionListResponse.Mission toMission(MissionDefinitionView definition, long current, boolean achieved) {
        return new MissionListResponse.Mission(
                definition.code(),
                definition.name(),
                definition.description(),
                definition.periodType() == MissionDefinition.PeriodType.DAILY
                        ? MissionListResponse.PeriodType.DAILY
                        : MissionListResponse.PeriodType.ONE_TIME,
                MissionListResponse.RewardType.INTERNAL_POINT,
                definition.rewardPointAmount(),
                current,
                definition.targetValue(),
                achieved ? MissionListResponse.Status.ACHIEVED : MissionListResponse.Status.IN_PROGRESS,
                achieved ? MissionListResponse.ClaimStatus.CLAIMABLE : MissionListResponse.ClaimStatus.LOCKED
        );
    }

    /**
     * 오늘부터 거꾸로 세어 하루도 빠지지 않은 날 수. 아직 오늘 기록이 없으면 어제부터 센다 —
     * 자정 직후에 스트릭이 0으로 보였다가 앱을 켜면 다시 붙는 현상을 막는다.
     *
     * <p>{@link #ATTENDANCE_LOOKBACK_DAYS} 일에서 멈춘다. 그 이상 연속 출석한 유저는 상한값으로 보인다.
     */
    private int consecutiveAttendanceDays(UUID userId, LocalDate today) {
        List<LocalDate> attendedDates = userTapDailyRepository.findRecentTapDates(
                userId, today, PageRequest.of(0, ATTENDANCE_LOOKBACK_DAYS));
        if (attendedDates.isEmpty()) {
            return 0;
        }

        LocalDate expected = attendedDates.getFirst().equals(today) ? today : today.minusDays(1);
        if (!attendedDates.getFirst().equals(expected)) {
            return 0;
        }

        int streak = 0;
        for (LocalDate attendedDate : attendedDates) {
            if (!attendedDate.equals(expected)) {
                break;
            }
            streak++;
            expected = expected.minusDays(1);
        }
        return streak;
    }

    public record DailyMissionFeed(
            List<MissionListResponse.Mission> missions,
            MissionListResponse.DailySummary summary
    ) {
    }
}
