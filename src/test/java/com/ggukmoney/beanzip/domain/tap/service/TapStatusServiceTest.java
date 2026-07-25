package com.ggukmoney.beanzip.domain.tap.service;

import com.ggukmoney.beanzip.domain.tap.dto.response.TapTodayStatusResponse;
import com.ggukmoney.beanzip.domain.tap.entity.UserTapDaily;
import com.ggukmoney.beanzip.domain.tap.entity.UserTapProgress;
import com.ggukmoney.beanzip.domain.user.entity.AppUser;
import com.ggukmoney.beanzip.domain.user.service.UserService;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TapStatusServiceTest {

    private final UserTapDailyService userTapDailyService = mock(UserTapDailyService.class);
    private final UserTapProgressService userTapProgressService = mock(UserTapProgressService.class);
    private final UserService userService = mock(UserService.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-07-20T15:00:00Z"), ZoneOffset.UTC);
    private final ZoneId businessZoneId = ZoneId.of("Asia/Seoul");
    private final TapStatusService tapStatusService =
            new TapStatusService(userTapDailyService, userTapProgressService, userService, clock, businessZoneId);

    private final UUID userId = UUID.randomUUID();
    private final LocalDate today = LocalDate.of(2026, 7, 21);

    @Test
    void returnsTodayCountsAndRemainingTapsToNextTargets() {
        AppUser user = mock(AppUser.class);
        when(userService.getById(userId)).thenReturn(user);

        UserTapDaily daily = UserTapDaily.createFor(user, today);
        daily.addValidTaps(120);
        daily.incrementPointEarned();
        when(userTapDailyService.getOrCreate(eq(user), eq(today))).thenReturn(daily);

        UserTapProgress progress = UserTapProgress.createFor(user, 300, 200);
        progress.addValidTaps(120);
        when(userTapProgressService.getForUser(userId)).thenReturn(progress);

        TapTodayStatusResponse response = tapStatusService.getTodayStatus(userId);

        assertThat(response.date()).isEqualTo(today);
        assertThat(response.validTapCount()).isEqualTo(120);
        assertThat(response.pointEarnedToday()).isEqualTo(1);
        assertThat(response.remainingTapsToNextPoint()).isEqualTo(180);
        assertThat(response.remainingTapsToNextBox()).isEqualTo(80);
    }

    @Test
    void clampsRemainingTapsAtZeroWhenTargetAlreadyReached() {
        AppUser user = mock(AppUser.class);
        when(userService.getById(userId)).thenReturn(user);

        UserTapDaily daily = UserTapDaily.createFor(user, today);
        when(userTapDailyService.getOrCreate(eq(user), eq(today))).thenReturn(daily);

        UserTapProgress progress = UserTapProgress.createFor(user, 100, 100);
        progress.addValidTaps(150);
        when(userTapProgressService.getForUser(userId)).thenReturn(progress);

        TapTodayStatusResponse response = tapStatusService.getTodayStatus(userId);

        assertThat(response.remainingTapsToNextPoint()).isZero();
        assertThat(response.remainingTapsToNextBox()).isZero();
    }
}
