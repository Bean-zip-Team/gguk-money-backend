package com.ggukmoney.beanzip.domain.mission.service;

import com.ggukmoney.beanzip.domain.mission.dto.response.MissionRewardClaimResponse;
import com.ggukmoney.beanzip.domain.mission.dto.response.MissionRewardListResponse;
import com.ggukmoney.beanzip.domain.promotion.dto.response.MissionListResponse;
import com.ggukmoney.beanzip.domain.promotion.service.MissionQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 상시 미션과 데일리 미션을 한 목록으로 합친다 (BEA-299).
 *
 * <p>두 미션은 저장 구조도 지급 방식도 다르지만 유저에게는 한 화면이어야 한다. 화면이 갈라지면
 * 둘 다 보지 않는다는 것이 BEA-300·BEA-301 의 전제다.
 */
@Service
@RequiredArgsConstructor
public class MissionFeedService {

    private final MissionQueryService missionQueryService;
    private final DailyMissionQueryService dailyMissionQueryService;
    private final MissionRewardService missionRewardService;
    private final Clock clock;

    /**
     * @param includeDaily 데일리 미션을 함께 내려줄지. <b>구버전 앱을 보호하는 장치다</b> — 지금 앱은
     *                     목록에 상시 미션만 온다고 보고 보상을 전부 토스 포인트로 그린다. 데일리
     *                     미션이 섞이면 내부 포인트가 토스 포인트로 표시되고, 달성했는데 지급되지
     *                     않는 상태가 화면에 남는다. 데일리 화면을 붙인 앱만 이 값을 켠다.
     */
    // 조회지만 쓰기 트랜잭션이다. 데일리 미션은 달성한 순간 보상 행을 만들어 둬야 "달성했지만
    // 아직 안 받은" 상태가 존재할 수 있다.
    @Transactional
    public MissionListResponse feedOf(UUID userId, boolean includeDaily) {
        List<MissionListResponse.Mission> missions = new ArrayList<>(missionQueryService.promotionMissionsOf(userId));
        if (!includeDaily) {
            return new MissionListResponse(missions, null);
        }

        DailyMissionQueryService.DailyMissionFeed daily = dailyMissionQueryService.feedOf(userId);
        missions.addAll(daily.missions());

        return new MissionListResponse(missions, daily.summary());
    }

    /**
     * 보상 하나를 받는다.
     *
     * <p>받기 전에 오늘의 미션을 다시 판정한다 — 목록을 열지 않고 바로 수령을 부르는 경로가 생겨도
     * 달성한 보상을 놓치지 않게 하기 위해서다. 판정과 수령은 <b>같은 시각</b>을 쓴다. 시각을 두 번
     * 재면 자정 경계에서 방금 만든 보상을 곧바로 소멸로 판정하는 일이 생긴다.
     */
    @Transactional
    public MissionRewardClaimResponse claim(UUID userId, UUID rewardId) {
        Instant now = clock.instant();
        dailyMissionQueryService.materializeRewards(userId, now);
        return toResponse(missionRewardService.claim(userId, rewardId, now));
    }

    /** 미수령 보상을 한 번에 받는다. 시안의 {@code 보상 2개 모두 받기} 가 이 경로다. */
    @Transactional
    public MissionRewardClaimResponse claimAll(UUID userId) {
        Instant now = clock.instant();
        dailyMissionQueryService.materializeRewards(userId, now);
        return toResponse(missionRewardService.claimAll(userId, now));
    }

    /** 보상 이력. 소멸한 보상을 "놓친 보상"으로 보여주는 데 쓴다. */
    @Transactional(readOnly = true)
    public MissionRewardListResponse rewardHistory(UUID userId, MissionRewardListResponse.RewardStatus status) {
        return missionRewardService.history(userId, status.toEntityStatus(), clock.instant());
    }

    private static MissionRewardClaimResponse toResponse(MissionRewardService.ClaimResult result) {
        return new MissionRewardClaimResponse(result.claimedCount(), result.claimedPointAmount(), result.pointBalance());
    }
}
