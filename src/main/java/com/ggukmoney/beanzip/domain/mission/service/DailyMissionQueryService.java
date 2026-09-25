package com.ggukmoney.beanzip.domain.mission.service;

import com.ggukmoney.beanzip.domain.mission.entity.MissionDefinition;
import com.ggukmoney.beanzip.domain.mission.entity.MissionReward;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * 오늘의 데일리 미션 목록과 진행도 (BEA-299).
 *
 * <p>진행도는 저장하지 않고 조회 시점에 계산한다. 탭 수·출석·알림 동의는 이미 다른 테이블이 들고
 * 있어 따로 적재하면 같은 사실이 두 군데 남는다.
 *
 * <p>반면 <b>달성 사실은 저장한다</b>. 수령이 수동이라 "달성했지만 아직 안 받은" 상태가 존재해야
 * 하고, 자정에 소멸한 보상도 기록으로 남아야 얼마를 놓쳤는지 보여줄 수 있다.
 */
@Service
@RequiredArgsConstructor
public class DailyMissionQueryService {

    /** 연속 출석을 세려고 읽는 최근 일자 수. 60일이면 화면에 쓰기 충분하고 쿼리도 가볍다. */
    private static final int ATTENDANCE_LOOKBACK_DAYS = 60;

    private final MissionDefinitionCatalog missionDefinitionCatalog;
    private final MissionRewardService missionRewardService;
    private final UserTapDailyRepository userTapDailyRepository;
    private final RankUpSignal rankUpSignal;
    private final NotificationOptInSignal notificationOptInSignal;
    private final ZoneId businessZoneId;
    private final Clock clock;

    @Transactional
    public DailyMissionFeed feedOf(UUID userId) {
        return feedOf(userId, clock.instant());
    }

    /**
     * 수령 경로처럼 시각을 이미 정해 둔 호출자를 위한 입구.
     *
     * <p>판정과 수령이 서로 다른 시각을 쓰면 자정 경계에서 방금 만든 보상을 곧바로 소멸로 판정하는
     * 일이 생긴다.
     */
    @Transactional
    public DailyMissionFeed feedOf(UUID userId, Instant now) {
        Judged judged = judge(userId, now);

        List<MissionListResponse.Mission> missions = new ArrayList<>();
        int dailyShownCount = 0;
        int completedCount = 0;
        long claimableRewardTotal = 0L;

        for (Evaluated mission : judged.evaluated()) {
            MissionReward reward = judged.rewardOf(mission);
            missions.add(toMission(mission, reward));

            // 받을 수 있는 보상은 단발성도 합산한다. 일괄 수령이 단발성 보상까지 지급하므로,
            // 여기서 빼면 화면에 적힌 금액과 실제로 들어오는 금액이 어긋난다.
            if (reward != null && reward.isClaimable() && !reward.hasExpiredAt(now)) {
                claimableRewardTotal += reward.getRewardPointAmount();
            }

            // 반면 진행도 게이지(2 / 8 완료)는 매일 반복되는 미션만 센다. 단발성 미션까지 넣으면
            // 한 번 받고 사라질 미션이 오늘의 진행도를 계속 부풀린다.
            if (mission.definition().periodType() != MissionDefinition.PeriodType.DAILY) {
                continue;
            }
            dailyShownCount++;
            if (mission.achieved()) {
                completedCount++;
            }
        }

        return new DailyMissionFeed(
                missions,
                new MissionListResponse.DailySummary(
                        completedCount,
                        dailyShownCount,
                        claimableRewardTotal,
                        judged.resetAt(),
                        consecutiveAttendanceDays(userId, judged.today())
                )
        );
    }

    /**
     * 달성한 미션의 보상 행만 만들어 둔다.
     *
     * <p>수령 경로가 쓴다. 목록을 열지 않고 바로 수령을 부르더라도 달성한 보상을 놓치지 않으면서,
     * 응답 조립에만 필요한 연속 출석 조회 같은 일은 하지 않는다.
     */
    @Transactional
    public void materializeRewards(UUID userId, Instant now) {
        judge(userId, now);
    }

    private Judged judge(UUID userId, Instant now) {
        LocalDate today = LocalDate.ofInstant(now, businessZoneId);
        Instant resetAt = today.plusDays(1).atStartOfDay(businessZoneId).toInstant();

        Map<MissionRewardService.RewardKey, MissionReward> rewards = new HashMap<>(missionRewardService.rewardsOf(
                userId, List.of(today.toString(), MissionReward.ONE_TIME_PERIOD_KEY)));

        List<Evaluated> evaluated = evaluate(userId, today, rewards);
        rewards.putAll(missionRewardService.createMissing(
                userId, achievedMissions(evaluated, today, resetAt), rewards, now));

        return new Judged(evaluated, rewards, today, resetAt);
    }

    private List<Evaluated> evaluate(
            UUID userId,
            LocalDate today,
            Map<MissionRewardService.RewardKey, MissionReward> rewards
    ) {
        List<MissionDefinitionView> definitions = missionDefinitionCatalog.activeDefinitions();

        Optional<UserTapDaily> todayTapDaily = userTapDailyRepository.findByUserIdAndTapDate(userId, today);
        long todayTapCount = todayTapDaily.map(daily -> (long) daily.getTotalValidTapCount()).orElse(0L);
        // 랭킹 미션이 꺼져 있으면 순위를 구할 이유가 없다. 이 조회는 목록·수령 경로에서 매번 돈다.
        Optional<Long> rankUp = hasMissionOfType(definitions, MissionDefinition.MissionType.RANK_UP)
                ? rankUpSignal.rankUpOf(userId, today)
                : Optional.empty();
        Optional<Boolean> notificationAgreed =
                hasMissionOfType(definitions, MissionDefinition.MissionType.NOTIFICATION_OPT_IN)
                        ? notificationOptInSignal.agreed(userId)
                        : Optional.empty();

        List<Evaluated> evaluated = new ArrayList<>();
        for (MissionDefinitionView definition : definitions) {
            MissionReward reward = rewards.get(rewardKeyOf(definition, today));
            if (definition.periodType() == MissionDefinition.PeriodType.ONE_TIME
                    && reward != null && reward.isClaimed()) {
                // 한 번 수행한 단발성 미션은 다시 뜨지 않는다. 나중에 알림을 꺼도 마찬가지다.
                continue;
            }

            Optional<Long> current =
                    currentValueOf(definition, todayTapDaily.isPresent(), todayTapCount, rankUp, notificationAgreed);
            if (current.isEmpty()) {
                // 오늘 판정할 수 없는 미션은 목록에서 뺀다. 달성할 수 없는 미션을 0/5 로 계속 보여주면
                // 유저는 눌러도 반응이 없는 줄 안다. 월요일의 랭킹 미션이 여기 해당한다.
                continue;
            }

            // 이미 보상 행이 있으면 달성한 것이다. 달성 후 조건이 무너져도(예: 알림을 껐다) 받을 수 있어야 한다.
            boolean achieved = reward != null || current.get() >= definition.targetValue();
            evaluated.add(new Evaluated(definition, today, current.get(), achieved));
        }
        return evaluated;
    }

    private List<MissionRewardService.AchievedMission> achievedMissions(
            List<Evaluated> evaluated,
            LocalDate today,
            Instant resetAt
    ) {
        return evaluated.stream()
                .filter(Evaluated::achieved)
                .map(mission -> new MissionRewardService.AchievedMission(
                        mission.definition().code(),
                        MissionRewardService.AchievedMission.periodKeyOf(mission.definition().periodType(), today),
                        mission.definition().rewardPointAmount(),
                        // 데일리 보상만 자정에 소멸한다. 단발성 보상은 만료가 없다.
                        mission.definition().periodType() == MissionDefinition.PeriodType.DAILY ? resetAt : null
                ))
                .toList();
    }

    /**
     * 미션 종류별 현재 값. 비어 있으면 <b>오늘은 판정할 수 없다</b>는 뜻이다.
     */
    private Optional<Long> currentValueOf(
            MissionDefinitionView definition,
            boolean attendedToday,
            long todayTapCount,
            Optional<Long> rankUp,
            Optional<Boolean> notificationAgreed
    ) {
        return switch (definition.missionType()) {
            // 앱을 켜면 홈이 오늘 탭 상태를 조회하면서 오늘자 행을 만든다. 그래서 행의 존재가 곧 출석이다.
            case ATTENDANCE -> Optional.of(attendedToday ? 1L : 0L);
            case TAP_COUNT -> Optional.of(todayTapCount);
            case RANK_UP -> rankUp;
            case NOTIFICATION_OPT_IN -> notificationAgreed.map(agreed -> agreed ? 1L : 0L);
        };
    }

    private static boolean hasMissionOfType(
            List<MissionDefinitionView> definitions,
            MissionDefinition.MissionType missionType
    ) {
        return definitions.stream().anyMatch(definition -> definition.missionType() == missionType);
    }

    private static MissionRewardService.RewardKey rewardKeyOf(MissionDefinitionView definition, LocalDate today) {
        return new MissionRewardService.RewardKey(
                definition.code(),
                MissionRewardService.AchievedMission.periodKeyOf(definition.periodType(), today)
        );
    }

    private MissionListResponse.Mission toMission(Evaluated mission, MissionReward reward) {
        MissionDefinitionView definition = mission.definition();
        return new MissionListResponse.Mission(
                definition.code(),
                definition.name(),
                definition.description(),
                missionTypeOf(definition.missionType()),
                definition.periodType() == MissionDefinition.PeriodType.DAILY
                        ? MissionListResponse.PeriodType.DAILY
                        : MissionListResponse.PeriodType.ONE_TIME,
                MissionListResponse.RewardType.INTERNAL_POINT,
                definition.rewardPointAmount(),
                mission.current(),
                definition.targetValue(),
                statusOf(mission, reward),
                claimStatusOf(reward),
                reward == null ? null : reward.getPublicId()
        );
    }

    /**
     * 화면이 종류마다 다르게 그려야 하므로 분류를 그대로 내려준다. code 문자열로 추측하게 두면
     * 미션 코드를 바꾸는 순간 화면이 깨지고, 정의를 서버가 내려준다는 전제도 무너진다.
     *
     * <p>switch 로 적는다. 종류가 추가되면 컴파일이 깨져 응답에 빠뜨리는 일을 막아준다.
     */
    private static MissionListResponse.MissionType missionTypeOf(MissionDefinition.MissionType missionType) {
        return switch (missionType) {
            case ATTENDANCE -> MissionListResponse.MissionType.ATTENDANCE;
            case TAP_COUNT -> MissionListResponse.MissionType.TAP_COUNT;
            case RANK_UP -> MissionListResponse.MissionType.RANK_UP;
            case NOTIFICATION_OPT_IN -> MissionListResponse.MissionType.NOTIFICATION_OPT_IN;
        };
    }

    private MissionListResponse.Status statusOf(Evaluated mission, MissionReward reward) {
        if (reward != null && reward.isClaimed()) {
            return MissionListResponse.Status.REWARDED;
        }
        return mission.achieved() ? MissionListResponse.Status.ACHIEVED : MissionListResponse.Status.IN_PROGRESS;
    }

    private MissionListResponse.ClaimStatus claimStatusOf(MissionReward reward) {
        if (reward == null) {
            return MissionListResponse.ClaimStatus.LOCKED;
        }
        if (reward.isClaimed()) {
            return MissionListResponse.ClaimStatus.CLAIMED;
        }
        return reward.isClaimable()
                ? MissionListResponse.ClaimStatus.CLAIMABLE
                : MissionListResponse.ClaimStatus.EXPIRED;
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

    private record Evaluated(MissionDefinitionView definition, LocalDate today, long current, boolean achieved) {
    }

    private record Judged(
            List<Evaluated> evaluated,
            Map<MissionRewardService.RewardKey, MissionReward> rewards,
            LocalDate today,
            Instant resetAt
    ) {

        MissionReward rewardOf(Evaluated mission) {
            return rewards.get(rewardKeyOf(mission.definition(), mission.today()));
        }
    }

    public record DailyMissionFeed(
            List<MissionListResponse.Mission> missions,
            MissionListResponse.DailySummary summary
    ) {
    }
}
