package com.ggukmoney.beanzip.domain.mission.service;

import com.ggukmoney.beanzip.domain.mission.entity.MissionDefinition;
import com.ggukmoney.beanzip.domain.mission.repository.MissionRewardRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DailyMissionNudgeServiceTest {

    private static final LocalDate TODAY = LocalDate.parse("2026-07-25");
    private static final String PERIOD_KEY = "2026-07-25";
    /** 랭킹 상승은 어제 순위가 있어야 판정되므로 기준에서 빠진다. */
    private static final List<String> CRITERIA_CODES = List.of("ATTENDANCE", "TAP_500", "TAP_1000");
    private static final Instant NOW = Instant.parse("2026-07-25T12:00:00Z");

    private final MissionRewardRepository missionRewardRepository = mock(MissionRewardRepository.class);
    private final MissionDefinitionCatalog missionDefinitionCatalog = mock(MissionDefinitionCatalog.class);
    private final DailyMissionNudgeService service =
            new DailyMissionNudgeService(missionRewardRepository, missionDefinitionCatalog);

    @BeforeEach
    void setUp() {
        // 출석·500탭·1000탭은 누구나 끝낼 수 있고, 랭킹 상승은 어제 순위가 있어야 판정된다.
        lenient().when(missionDefinitionCatalog.activeDefinitions()).thenReturn(List.of(
                dailyDefinition("ATTENDANCE", MissionDefinition.MissionType.ATTENDANCE),
                dailyDefinition("TAP_500", MissionDefinition.MissionType.TAP_COUNT),
                dailyDefinition("TAP_1000", MissionDefinition.MissionType.TAP_COUNT),
                dailyDefinition("RANK_UP", MissionDefinition.MissionType.RANK_UP),
                oneTimeDefinition("NOTIFICATION_OPT_IN")
        ));
        lenient().when(missionRewardRepository.findUserIdsWithClaimableRewardsInPeriod(any(), any(), any()))
                .thenReturn(List.of());
        lenient().when(missionRewardRepository.countRewardsInPeriod(any(), any(), any())).thenReturn(List.of());
    }

    @Test
    void picksUsersWhoStillHaveARewardToClaimToday() {
        UUID claimableUserId = UUID.randomUUID();
        UUID claimedUserId = UUID.randomUUID();
        List<UUID> userIds = List.of(claimableUserId, claimedUserId);
        when(missionRewardRepository.findUserIdsWithClaimableRewardsInPeriod(userIds, PERIOD_KEY, NOW))
                .thenReturn(List.of(claimableUserId));
        when(missionRewardRepository.countRewardsInPeriod(userIds, PERIOD_KEY, CRITERIA_CODES))
                .thenReturn(List.of(rewardCount(claimableUserId, 3), rewardCount(claimedUserId, 3)));

        assertThat(service.usersNeedingNudge(service.criteriaOf(TODAY), userIds, NOW)).containsExactly(claimableUserId);
    }

    @Test
    void picksUsersWhoStillHaveMissionsLeftEvenIfTheyAlreadyClaimedOne() {
        UUID halfwayUserId = UUID.randomUUID();
        UUID doneUserId = UUID.randomUUID();
        List<UUID> userIds = List.of(halfwayUserId, doneUserId);
        // 오전에 출석 하나만 받아 간 유저다. 받을 보상은 없지만 자정에 사라질 미션이 남아 있다.
        when(missionRewardRepository.countRewardsInPeriod(userIds, PERIOD_KEY, CRITERIA_CODES))
                .thenReturn(List.of(rewardCount(halfwayUserId, 1), rewardCount(doneUserId, 3)));

        assertThat(service.usersNeedingNudge(service.criteriaOf(TODAY), userIds, NOW)).containsExactly(halfwayUserId);
    }

    @Test
    void picksUsersWhoHaveNotStartedToday() {
        UUID idleUserId = UUID.randomUUID();
        UUID doneUserId = UUID.randomUUID();
        List<UUID> userIds = List.of(idleUserId, doneUserId);
        when(missionRewardRepository.countRewardsInPeriod(userIds, PERIOD_KEY, CRITERIA_CODES))
                .thenReturn(List.of(rewardCount(doneUserId, 3)));

        assertThat(service.usersNeedingNudge(service.criteriaOf(TODAY), userIds, NOW)).containsExactly(idleUserId);
    }

    @Test
    void countsOnlyTheMissionsTheCriteriaIsBuiltFrom() {
        // 기준에서 뺀 랭킹 보상이 달성 수에 섞이면 그 한 건이 다른 미션 한 건의 자리를 채운다.
        // 그래서 기준을 세울 때 쓴 코드를 세는 쿼리에도 그대로 넘겨야 한다.
        DailyMissionNudgeService.NudgeCriteria criteria = service.criteriaOf(TODAY);
        assertThat(criteria.missionCodes()).containsExactly("ATTENDANCE", "TAP_500", "TAP_1000");

        service.usersNeedingNudge(criteria, List.of(UUID.randomUUID()), NOW);

        verify(missionRewardRepository).countRewardsInPeriod(any(), eq(PERIOD_KEY), eq(CRITERIA_CODES));
    }

    @Test
    void leavesOutUsersWhoFinishedEverythingExceptTheRankUpMission() {
        UUID userId = UUID.randomUUID();
        List<UUID> userIds = List.of(userId);
        // 어제 순위가 없어 랭킹 미션이 목록에서 빠진 유저다. 이 미션까지 기준에 넣으면 나머지를
        // 다 끝내도 매일 밤 알림을 받게 된다.
        when(missionRewardRepository.countRewardsInPeriod(userIds, PERIOD_KEY, CRITERIA_CODES))
                .thenReturn(List.of(rewardCount(userId, 3)));

        assertThat(service.usersNeedingNudge(service.criteriaOf(TODAY), userIds, NOW)).isEmpty();
    }

    @Test
    void nudgesNobodyWhenTheCatalogIsEmpty() {
        UUID userId = UUID.randomUUID();
        when(missionDefinitionCatalog.activeDefinitions()).thenReturn(List.of());

        // 미션 정의를 읽지 못한 상태다. 기준이 없으므로 전원에게 보내는 대신 조용히 넘어간다.
        assertThat(service.usersNeedingNudge(service.criteriaOf(TODAY), List.of(userId), NOW)).isEmpty();
        verify(missionRewardRepository, never()).countRewardsInPeriod(any(), any(), any());
    }

    @Test
    void asksNothingWhenThePageIsEmpty() {
        assertThat(service.usersNeedingNudge(service.criteriaOf(TODAY), List.of(), NOW)).isEmpty();

        verify(missionRewardRepository, never()).findUserIdsWithClaimableRewardsInPeriod(any(), any(), any());
        verify(missionRewardRepository, never()).countRewardsInPeriod(any(), any(), any());
    }

    private MissionRewardRepository.UserRewardCount rewardCount(UUID userId, long count) {
        return new MissionRewardRepository.UserRewardCount() {
            @Override
            public UUID getUserId() {
                return userId;
            }

            @Override
            public long getRewardCount() {
                return count;
            }
        };
    }

    private MissionDefinitionView dailyDefinition(String code, MissionDefinition.MissionType missionType) {
        return definition(code, missionType, MissionDefinition.PeriodType.DAILY);
    }

    private MissionDefinitionView oneTimeDefinition(String code) {
        return definition(code, MissionDefinition.MissionType.NOTIFICATION_OPT_IN, MissionDefinition.PeriodType.ONE_TIME);
    }

    private MissionDefinitionView definition(
            String code,
            MissionDefinition.MissionType missionType,
            MissionDefinition.PeriodType periodType
    ) {
        return new MissionDefinitionView(code, missionType, periodType, code, code, 1L, 100, 0);
    }
}
