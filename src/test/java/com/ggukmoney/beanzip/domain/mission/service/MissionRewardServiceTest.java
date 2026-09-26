package com.ggukmoney.beanzip.domain.mission.service;

import com.ggukmoney.beanzip.domain.mission.dto.response.MissionRewardListResponse;
import com.ggukmoney.beanzip.domain.mission.entity.MissionDefinition;
import com.ggukmoney.beanzip.domain.mission.entity.MissionReward;
import com.ggukmoney.beanzip.domain.mission.repository.MissionRewardRepository;
import com.ggukmoney.beanzip.domain.point.entity.PointAccount;
import com.ggukmoney.beanzip.domain.point.service.PointAccountService;
import com.ggukmoney.beanzip.domain.point.service.PointLedgerService;
import com.ggukmoney.beanzip.domain.ranking.boost.SystemRankingBoostPolicy;
import com.ggukmoney.beanzip.domain.user.entity.AppUser;
import com.ggukmoney.beanzip.domain.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MissionRewardServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-21T05:00:00Z");
    private static final Instant MIDNIGHT = Instant.parse("2026-09-21T15:00:00Z");

    private final MissionRewardRepository missionRewardRepository = mock(MissionRewardRepository.class);
    private final PointAccountService pointAccountService = mock(PointAccountService.class);
    private final PointLedgerService pointLedgerService = mock(PointLedgerService.class);
    private final UserService userService = mock(UserService.class);
    private final MissionDefinitionCatalog missionDefinitionCatalog = mock(MissionDefinitionCatalog.class);
    private final SystemRankingBoostPolicy internalAccountPolicy = mock(SystemRankingBoostPolicy.class);

    private final MissionRewardService service = new MissionRewardService(
            missionRewardRepository, pointAccountService, pointLedgerService, userService,
            missionDefinitionCatalog, internalAccountPolicy);

    private final UUID userId = UUID.randomUUID();
    private final PointAccount account = mock(PointAccount.class);
    private final AppUser user = mock(AppUser.class);

    @BeforeEach
    void stubPointPayout() {
        lenient().when(user.getId()).thenReturn(userId);
        lenient().when(userService.getById(userId)).thenReturn(user);
        lenient().when(pointAccountService.credit(eq(userId), anyLong())).thenReturn(account);
        lenient().when(pointAccountService.getBalance(userId)).thenReturn(1380L);
        lenient().when(missionDefinitionCatalog.findByCode("RANK_UP_5"))
                .thenReturn(Optional.of(definition("RANK_UP_5", MissionDefinition.MissionType.RANK_UP)));
        lenient().when(missionDefinitionCatalog.findByCode("ATTENDANCE"))
                .thenReturn(Optional.of(definition("ATTENDANCE", MissionDefinition.MissionType.ATTENDANCE)));
    }

    @Test
    void doesNotPayAnInternalAccountForARankUpItsBoostCouldHaveMade() {
        internalAccounts(Set.of(userId));
        MissionReward reward = claimableReward("RANK_UP_5", "2026-09-21", 100, MIDNIGHT);
        when(missionRewardRepository.findByUserIdAndPublicIdForUpdate(userId, reward.getPublicId()))
                .thenReturn(Optional.of(reward));

        MissionRewardService.ClaimResult result = service.claim(userId, reward.getPublicId(), NOW);

        // 부스트로 하루에 수십 등을 오르므로 사내 계정은 이 미션을 매일 달성한다. 화면에서는 정상 수령처럼
        // 보이게 두고(주간 상금과 같은 규칙) 포인트만 넣지 않는다.
        assertThat(reward.isClaimed()).isTrue();
        assertThat(result.claimedPointAmount()).isEqualTo(100L);
        verify(pointAccountService, never()).credit(any(), anyLong());
        verify(pointLedgerService, never()).recordCredit(any(), any(), anyLong(), anyString(), any());
    }

    @Test
    void stillPaysAnInternalAccountForMissionsTheBoostCannotTouch() {
        internalAccounts(Set.of(userId));
        MissionReward reward = claimableReward("ATTENDANCE", "2026-09-21", 100, MIDNIGHT);
        when(missionRewardRepository.findByUserIdAndPublicIdForUpdate(userId, reward.getPublicId()))
                .thenReturn(Optional.of(reward));

        service.claim(userId, reward.getPublicId(), NOW);

        verify(pointAccountService).credit(userId, 100L);
    }

    @Test
    void paysARealUserForTheRankUp() {
        internalAccounts(Set.of(UUID.randomUUID()));
        MissionReward reward = claimableReward("RANK_UP_5", "2026-09-21", 100, MIDNIGHT);
        when(missionRewardRepository.findByUserIdAndPublicIdForUpdate(userId, reward.getPublicId()))
                .thenReturn(Optional.of(reward));

        service.claim(userId, reward.getPublicId(), NOW);

        verify(pointAccountService).credit(userId, 100L);
    }

    @Test
    void claimAllAlsoWithholdsTheRankUpFromAnInternalAccount() {
        internalAccounts(Set.of(userId));
        MissionReward rankUp = claimableReward("RANK_UP_5", "2026-09-21", 100, MIDNIGHT);
        MissionReward attendance = claimableReward("ATTENDANCE", "2026-09-21", 100, MIDNIGHT);
        when(missionRewardRepository.findClaimablesForUpdate(userId, NOW)).thenReturn(List.of(rankUp, attendance));

        service.claimAll(userId, NOW);

        verify(pointAccountService).credit(userId, 100L);
        assertThat(rankUp.isClaimed()).isTrue();
    }

    private void internalAccounts(Set<UUID> ids) {
        when(internalAccountPolicy.load(NOW))
                .thenReturn(Optional.of(new SystemRankingBoostPolicy.Snapshot(false, ids, 1000L, 200, 500)));
    }

    private static MissionDefinitionView definition(String code, MissionDefinition.MissionType type) {
        return new MissionDefinitionView(code, type, MissionDefinition.PeriodType.DAILY, code, code, 1L, 100L, 0);
    }

    @Test
    void creditsPointsWithAKeyThatRepeatsForTheSameMissionAndDay() {
        MissionReward reward = claimableReward("TAP_500", "2026-09-21", 15, MIDNIGHT);
        when(missionRewardRepository.findByUserIdAndPublicIdForUpdate(userId, reward.getPublicId()))
                .thenReturn(Optional.of(reward));

        MissionRewardService.ClaimResult result = service.claim(userId, reward.getPublicId(), NOW);

        assertThat(result.claimedCount()).isEqualTo(1);
        assertThat(result.claimedPointAmount()).isEqualTo(15L);
        assertThat(result.pointBalance()).isEqualTo(1380L);
        verify(pointAccountService).credit(userId, 15L);
        // 같은 미션·같은 날짜면 같은 키가 나온다. 원장의 유니크 제약이 두 번째 적립을 막는 근거다.
        verify(pointLedgerService).recordCredit(account, user, 15L, "DAILY_MISSION_REWARD",
                UUID.nameUUIDFromBytes((userId + "-TAP_500-2026-09-21").getBytes(StandardCharsets.UTF_8)));
        assertThat(reward.isClaimed()).isTrue();
    }

    @Test
    void locksTheRewardRowSoTwoTapsCannotBothPassTheStatusCheck() {
        MissionReward reward = claimableReward("TAP_500", "2026-09-21", 15, MIDNIGHT);
        when(missionRewardRepository.findByUserIdAndPublicIdForUpdate(userId, reward.getPublicId()))
                .thenReturn(Optional.of(reward));

        service.claim(userId, reward.getPublicId(), NOW);

        // 잠그지 않고 읽으면 두 트랜잭션이 같은 CLAIMABLE 을 보고 둘 다 지급으로 넘어간다.
        verify(missionRewardRepository).findByUserIdAndPublicIdForUpdate(userId, reward.getPublicId());
        verify(missionRewardRepository, never()).findByUserIdAndMissionCodeAndPeriodKey(any(), anyString(), anyString());
    }

    @Test
    void refusesToPayTheSameRewardTwice() {
        MissionReward reward = claimableReward("TAP_500", "2026-09-21", 15, MIDNIGHT);
        reward.claim(NOW);
        when(missionRewardRepository.findByUserIdAndPublicIdForUpdate(userId, reward.getPublicId()))
                .thenReturn(Optional.of(reward));

        assertThatThrownBy(() -> service.claim(userId, reward.getPublicId(), NOW))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("MISSION_REWARD_ALREADY_CLAIMED")
                .extracting(exception -> ((ResponseStatusException) exception).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
        verify(pointAccountService, never()).credit(eq(userId), anyLong());
    }

    @Test
    void refusesARewardThatAlreadyPassedMidnight() {
        MissionReward reward = claimableReward("TAP_500", "2026-09-20", 15, Instant.parse("2026-09-20T15:00:00Z"));
        when(missionRewardRepository.findByUserIdAndPublicIdForUpdate(userId, reward.getPublicId()))
                .thenReturn(Optional.of(reward));

        assertThatThrownBy(() -> service.claim(userId, reward.getPublicId(), NOW))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("MISSION_REWARD_EXPIRED")
                .extracting(exception -> ((ResponseStatusException) exception).getStatusCode())
                .isEqualTo(HttpStatus.GONE);
        verify(pointAccountService, never()).credit(eq(userId), anyLong());
    }

    @Test
    void claimsEveryRewardThatIsStillAliveAndSkipsTheExpiredOnes() {
        MissionReward alive = claimableReward("ATTENDANCE", "2026-09-21", 100, MIDNIGHT);
        MissionReward expired = claimableReward("TAP_500", "2026-09-20", 15, Instant.parse("2026-09-20T15:00:00Z"));
        when(missionRewardRepository.findClaimablesForUpdate(userId, NOW)).thenReturn(List.of(alive, expired));

        MissionRewardService.ClaimResult result = service.claimAll(userId, NOW);

        // 소멸한 한 건 때문에 일괄 수령이 통째로 실패하면 받을 수 있는 나머지까지 못 받는다.
        assertThat(result.claimedCount()).isEqualTo(1);
        assertThat(result.claimedPointAmount()).isEqualTo(100L);
        assertThat(alive.isClaimed()).isTrue();
        assertThat(expired.isClaimable()).isTrue();
    }

    @Test
    void createsRewardsOnlyForAchievedMissionsThatDoNotHaveOneYet() {
        MissionReward existing = claimableReward("ATTENDANCE", "2026-09-21", 100, MIDNIGHT);
        MissionReward inserted = claimableReward("TAP_500", "2026-09-21", 15, MIDNIGHT);
        when(missionRewardRepository.findByUserIdAndMissionCodeAndPeriodKey(userId, "TAP_500", "2026-09-21"))
                .thenReturn(Optional.of(inserted));

        Map<MissionRewardService.RewardKey, MissionReward> created = service.createMissing(
                userId,
                List.of(
                        new MissionRewardService.AchievedMission("ATTENDANCE", "2026-09-21", 100, MIDNIGHT),
                        new MissionRewardService.AchievedMission("TAP_500", "2026-09-21", 15, MIDNIGHT)
                ),
                Map.of(new MissionRewardService.RewardKey("ATTENDANCE", "2026-09-21"), existing),
                NOW
        );

        assertThat(created).containsOnlyKeys(new MissionRewardService.RewardKey("TAP_500", "2026-09-21"));
        verify(missionRewardRepository).insertClaimableIfAbsent(
                any(), eq(userId), eq("TAP_500"), eq("2026-09-21"), eq(15L), eq(NOW), eq(MIDNIGHT), eq(NOW));
        verify(missionRewardRepository, never()).insertClaimableIfAbsent(
                any(), eq(userId), eq("ATTENDANCE"), anyString(), anyLong(), any(), any(), any());
    }

    @Test
    void tellsApartTheSameMissionInDifferentPeriods() {
        MissionReward yesterday = claimableReward("ATTENDANCE", "2026-09-20", 100, MIDNIGHT);
        when(missionRewardRepository.findByUserIdAndPeriodKeyIn(userId, List.of("2026-09-20", "2026-09-21")))
                .thenReturn(List.of(yesterday));

        Map<MissionRewardService.RewardKey, MissionReward> rewards =
                service.rewardsOf(userId, List.of("2026-09-20", "2026-09-21"));

        // 코드만으로 키를 잡으면 같은 미션의 다른 날짜 보상이 서로를 덮어쓴다.
        assertThat(rewards).containsOnlyKeys(new MissionRewardService.RewardKey("ATTENDANCE", "2026-09-20"));
    }

    @Test
    void keepsRewardsThatAlreadyPassedMidnightOutOfTheClaimableHistory() {
        MissionReward alive = claimableReward("ATTENDANCE", "2026-09-21", 100, MIDNIGHT);
        MissionReward expired = claimableReward("TAP_500", "2026-09-20", 15, Instant.parse("2026-09-20T15:00:00Z"));
        when(missionRewardRepository.findByUserIdAndStatusOrderByAchievedAtDesc(userId, MissionReward.Status.CLAIMABLE))
                .thenReturn(List.of(alive, expired));

        MissionRewardListResponse response = service.history(userId, MissionReward.Status.CLAIMABLE, NOW);

        // 자정 배치가 돌기 전이라 상태만 CLAIMABLE 일 뿐 받을 수 없다. 합계에 넣으면 화면 금액이 어긋난다.
        assertThat(response.totalPointAmount()).isEqualTo(100L);
        assertThat(response.rewards()).extracting(MissionRewardListResponse.Reward::missionCode)
                .containsExactly("ATTENDANCE");
    }

    @Test
    void listsExpiredRewardsWithTheirTotalSoTheAppCanShowWhatWasMissed() {
        when(missionRewardRepository.findByUserIdAndStatusOrderByAchievedAtDesc(userId, MissionReward.Status.EXPIRED))
                .thenReturn(List.of(
                        expiredReward("TAP_500", "2026-09-20", 15),
                        expiredReward("ATTENDANCE", "2026-09-20", 100)
                ));

        MissionRewardListResponse response = service.history(userId, MissionReward.Status.EXPIRED, NOW);

        assertThat(response.totalPointAmount()).isEqualTo(115L);
        assertThat(response.rewards())
                .extracting(MissionRewardListResponse.Reward::missionCode, MissionRewardListResponse.Reward::status)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("TAP_500", MissionRewardListResponse.RewardStatus.EXPIRED),
                        org.assertj.core.groups.Tuple.tuple("ATTENDANCE", MissionRewardListResponse.RewardStatus.EXPIRED)
                );
    }

    @Test
    void reportsMissingRewardsInsteadOfPayingBlind() {
        UUID unknownRewardId = UUID.randomUUID();
        when(missionRewardRepository.findByUserIdAndPublicIdForUpdate(userId, unknownRewardId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.claim(userId, unknownRewardId, NOW))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("MISSION_REWARD_NOT_FOUND");
        verify(pointLedgerService, never()).recordCredit(any(), any(), anyLong(), anyString(), any());
    }

    private MissionReward claimableReward(String missionCode, String periodKey, long amount, Instant expiresAt) {
        return MissionReward.claimable(userId, missionCode, periodKey, amount, NOW, expiresAt);
    }

    private MissionReward expiredReward(String missionCode, String periodKey, long amount) {
        MissionReward reward = claimableReward(missionCode, periodKey, amount, Instant.parse("2026-09-20T15:00:00Z"));
        reward.expire();
        return reward;
    }
}
