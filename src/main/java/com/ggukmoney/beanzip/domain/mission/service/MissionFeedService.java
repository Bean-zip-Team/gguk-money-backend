package com.ggukmoney.beanzip.domain.mission.service;

import com.ggukmoney.beanzip.domain.promotion.dto.response.MissionListResponse;
import com.ggukmoney.beanzip.domain.promotion.service.MissionQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    /**
     * @param includeDaily 데일리 미션을 함께 내려줄지. <b>구버전 앱을 보호하는 장치다</b> — 지금 앱은
     *                     목록에 상시 미션만 온다고 보고 보상을 전부 토스 포인트로 그린다. 데일리
     *                     미션이 섞이면 내부 포인트가 토스 포인트로 표시되고, 달성했는데 지급되지
     *                     않는 상태가 화면에 남는다. 데일리 화면을 붙인 앱만 이 값을 켠다.
     */
    @Transactional(readOnly = true)
    public MissionListResponse feedOf(UUID userId, boolean includeDaily) {
        List<MissionListResponse.Mission> missions = new ArrayList<>(missionQueryService.promotionMissionsOf(userId));
        if (!includeDaily) {
            return new MissionListResponse(missions, null);
        }

        DailyMissionQueryService.DailyMissionFeed daily = dailyMissionQueryService.feedOf(userId);
        missions.addAll(daily.missions());

        return new MissionListResponse(missions, daily.summary());
    }
}
