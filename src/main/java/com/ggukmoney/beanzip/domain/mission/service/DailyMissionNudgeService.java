package com.ggukmoney.beanzip.domain.mission.service;

import com.ggukmoney.beanzip.domain.mission.entity.MissionDefinition;
import com.ggukmoney.beanzip.domain.mission.repository.MissionRewardRepository;
import com.ggukmoney.beanzip.domain.mission.service.MissionRewardService.AchievedMission;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 저녁 알림을 보낼 대상을 고른다 (BEA-299).
 *
 * <p>두 부류다. <b>오늘 받을 보상이 남은 유저</b>와 <b>오늘 아직 끝내지 못한 미션이 있는 유저</b>다.
 * 오늘 미션을 모두 끝내고 보상까지 받아 간 유저에게는 보내지 않는다 — 할 일이 없는데 오는 알림은
 * 스팸으로 읽히고, 알림 동의를 해제하는 이유가 된다.
 */
@Service
@RequiredArgsConstructor
public class DailyMissionNudgeService {

    private final MissionRewardRepository missionRewardRepository;
    private final MissionDefinitionCatalog missionDefinitionCatalog;

    /**
     * 이번 발송에 쓸 판정 기준. 배치 시작 시점에 한 번 만들어 모든 페이지가 공유한다.
     *
     * <p>미션 정의는 60초 주기로 다시 읽으므로, 페이지마다 기준을 새로 세우면 발송이 길어질 때
     * 앞 페이지와 뒤 페이지가 서로 다른 기준으로 판정된다.
     */
    public NudgeCriteria criteriaOf(LocalDate today) {
        return new NudgeCriteria(
                AchievedMission.periodKeyOf(MissionDefinition.PeriodType.DAILY, today),
                missionCodesEveryoneCanFinish()
        );
    }

    @Transactional(readOnly = true)
    public Set<UUID> usersNeedingNudge(NudgeCriteria criteria, List<UUID> userIds, Instant now) {
        if (userIds.isEmpty()) {
            return Set.of();
        }

        Set<UUID> needsNudge = new HashSet<>(missionRewardRepository.findUserIdsWithClaimableRewardsInPeriod(
                userIds, criteria.periodKey(), now));
        if (criteria.missionCodes().isEmpty()) {
            // 기준을 세울 수 없다. 전원에게 보내는 대신 받을 보상이 남은 유저만 남긴다.
            return needsNudge;
        }

        Map<UUID, Long> achievedCounts = new HashMap<>();
        missionRewardRepository.countRewardsInPeriod(userIds, criteria.periodKey(), criteria.missionCodes())
                .forEach(row -> achievedCounts.put(row.getUserId(), row.getRewardCount()));
        userIds.stream()
                .filter(userId -> achievedCounts.getOrDefault(userId, 0L) < criteria.missionCodes().size())
                .forEach(needsNudge::add);

        return needsNudge;
    }

    /**
     * 오늘 누구나 끝낼 수 있는 데일리 미션의 코드.
     *
     * <p>랭킹 상승 미션은 뺀다. 어제 순위가 없으면 판정 자체를 하지 않아 목록에서 빠지는데(월요일과
     * 어제 쉰 유저가 여기 해당한다), 그 미션까지 기준에 넣으면 그 유저들은 나머지를 다 끝내도 영원히
     * 기준에 못 미쳐 매일 밤 알림을 받는다.
     *
     * <p>단발성 미션도 뺀다. 하루 안에 끝내야 하는 일이 아니므로 저녁에 재촉할 이유가 없다.
     *
     * <p>개수가 아니라 코드를 넘기는 이유는, 달성한 수를 셀 때도 <b>같은 미션만</b> 세어야 하기
     * 때문이다. 기준에서 뺀 랭킹 보상이 달성 수에 섞이면 그 한 건이 다른 미션 한 건의 자리를 채워,
     * 아직 남은 미션이 있는데도 알림이 가지 않는다.
     */
    private List<String> missionCodesEveryoneCanFinish() {
        return missionDefinitionCatalog.activeDefinitions().stream()
                .filter(definition -> definition.periodType() == MissionDefinition.PeriodType.DAILY)
                .filter(definition -> definition.missionType() != MissionDefinition.MissionType.RANK_UP)
                .map(MissionDefinitionView::code)
                .toList();
    }

    public record NudgeCriteria(String periodKey, List<String> missionCodes) {
    }
}
