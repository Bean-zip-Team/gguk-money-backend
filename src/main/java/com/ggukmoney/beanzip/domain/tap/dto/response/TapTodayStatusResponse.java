package com.ggukmoney.beanzip.domain.tap.dto.response;

import java.time.LocalDate;

public record TapTodayStatusResponse(
        LocalDate date,
        int validTapCount,
        int pointEarnedToday,
        int remainingTapsToNextPoint,
        int remainingTapsToNextBox
) {
}
