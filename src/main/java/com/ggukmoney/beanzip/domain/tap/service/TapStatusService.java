package com.ggukmoney.beanzip.domain.tap.service;

import com.ggukmoney.beanzip.domain.tap.dto.response.TapTodayStatusResponse;
import com.ggukmoney.beanzip.domain.tap.entity.UserTapDaily;
import com.ggukmoney.beanzip.domain.tap.entity.UserTapProgress;
import com.ggukmoney.beanzip.domain.user.entity.AppUser;
import com.ggukmoney.beanzip.domain.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TapStatusService {

    private final UserTapDailyService userTapDailyService;
    private final UserTapProgressService userTapProgressService;
    private final UserService userService;

    public TapTodayStatusResponse getTodayStatus(UUID userId) {
        AppUser user = userService.getById(userId);
        UserTapDaily daily = userTapDailyService.getOrCreateToday(user);
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
