package com.ggukmoney.beanzip.domain.tap.service;

import com.ggukmoney.beanzip.domain.tap.dto.response.TapTodayStatusResponse;
import com.ggukmoney.beanzip.domain.tap.entity.UserTapDaily;
import com.ggukmoney.beanzip.domain.tap.entity.UserTapProgress;
import com.ggukmoney.beanzip.domain.user.entity.AppUser;
import com.ggukmoney.beanzip.domain.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TapStatusService {

    private final UserTapDailyService userTapDailyService;
    private final UserTapProgressService userTapProgressService;
    private final UserService userService;
    private final Clock clock;
    private final ZoneId businessZoneId;

    public TapTodayStatusResponse getTodayStatus(UUID userId) {
        AppUser user = userService.getById(userId);
        LocalDate tapDate = LocalDate.ofInstant(clock.instant(), businessZoneId);
        UserTapDaily daily = userTapDailyService.getOrCreate(user, tapDate);
        UserTapProgress progress = userTapProgressService.getForUser(userId);

        int remainingToNextPoint = (int) Math.max(progress.getNextPointTarget() - progress.getCumulativeValidTapCount(), 0);
        int remainingToNextBox = (int) Math.max(progress.getNextBoxTarget() - progress.getCumulativeValidTapCount(), 0);

        return new TapTodayStatusResponse(
                daily.getTapDate(),
                daily.getValidTapCount(),
                daily.getPointEarnedAmount(),
                remainingToNextPoint,
                remainingToNextBox
        );
    }
}
