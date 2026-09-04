package com.ggukmoney.beanzip.domain.tap.service;

import com.ggukmoney.beanzip.domain.tap.dto.BoxProgressSnapshot;
import com.ggukmoney.beanzip.domain.tap.dto.response.TapTodayStatusResponse;
import com.ggukmoney.beanzip.domain.tap.entity.UserTapDaily;
import com.ggukmoney.beanzip.domain.tap.entity.UserTapProgress;
import com.ggukmoney.beanzip.domain.user.entity.AppUser;
import com.ggukmoney.beanzip.domain.user.service.UserService;
import com.ggukmoney.beanzip.global.config.TapPolicyConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TapStatusService {

    private final UserTapDailyService userTapDailyService;
    private final UserTapProgressService userTapProgressService;
    private final UserTapSessionService userTapSessionService;
    private final UserService userService;
    private final TapPolicyConfig tapPolicyConfig;
    private final Clock clock;
    private final ZoneId businessZoneId;

    public TapTodayStatusResponse getTodayStatus(UUID userId) {
        AppUser user = userService.getById(userId);
        Instant now = clock.instant();
        LocalDate tapDate = LocalDate.ofInstant(now, businessZoneId);
        UserTapDaily daily = userTapDailyService.getOrCreate(user, tapDate);
        UserTapProgress progress = userTapProgressService.getForUser(userId);
        // 조회는 세션을 저장하지 않는다. 만료된 세션의 리셋을 여기서 영속화하면 탭 배치와
        // 같은 행을 동시에 갱신하게 된다 (UserTapSessionService#getBoxProgress 참고).
        BoxProgressSnapshot boxProgress = userTapSessionService.getBoxProgress(user, now, tapPolicyConfig);

        int remainingToNextPoint = userTapProgressService.remainingTapsToNextPoint(progress, daily, tapPolicyConfig);
        int remainingToNextBox = (int) Math.max(boxProgress.nextBoxTarget() - boxProgress.cumulativeValidTapCount(), 0);

        // 화면의 "오늘 탭"은 보상 상한(tap.validity.maxPerDay)과 무관하게 실제로 친 탭 수를 보여준다.
        // validTapCount 는 상한에서 멈추므로 상한 없이 누적되는 totalValidTapCount 를 반환한다.
        return new TapTodayStatusResponse(
                daily.getTapDate(),
                daily.getTotalValidTapCount(),
                daily.getPointEarnedAmount(),
                remainingToNextPoint,
                remainingToNextBox,
                boxProgress.cumulativeValidTapCount(),
                boxProgress.nextBoxTarget()
        );
    }
}
