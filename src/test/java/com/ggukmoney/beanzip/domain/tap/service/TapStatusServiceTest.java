package com.ggukmoney.beanzip.domain.tap.service;

import com.ggukmoney.beanzip.domain.tap.dto.response.TapTodayStatusResponse;
import com.ggukmoney.beanzip.domain.tap.entity.UserTapDaily;
import com.ggukmoney.beanzip.domain.tap.entity.UserTapProgress;
import com.ggukmoney.beanzip.domain.tap.entity.UserTapSession;
import com.ggukmoney.beanzip.domain.user.entity.AppUser;
import com.ggukmoney.beanzip.domain.user.service.UserService;
import com.ggukmoney.beanzip.global.config.TapPolicyConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TapStatusServiceTest {

    private final UserTapDailyService userTapDailyService = mock(UserTapDailyService.class);
    private final UserTapProgressService userTapProgressService = mock(UserTapProgressService.class);
    private final UserTapSessionService userTapSessionService = mock(UserTapSessionService.class);
    private final UserService userService = mock(UserService.class);
    private final TapPolicyConfig tapPolicyConfig = mock(TapPolicyConfig.class);
    private final Instant now = Instant.parse("2026-07-20T15:00:00Z");
    private final Clock clock = Clock.fixed(now, ZoneOffset.UTC);
    private final ZoneId businessZoneId = ZoneId.of("Asia/Seoul");
    private final TapStatusService tapStatusService = new TapStatusService(
            userTapDailyService, userTapProgressService, userTapSessionService, userService, tapPolicyConfig,
            clock, businessZoneId
    );

    private final UUID userId = UUID.randomUUID();
    private final LocalDate today = LocalDate.of(2026, 7, 21);

    @BeforeEach
    void useRealRemainingTapsCalculation() {
        // 남은 탭 계산은 상한 상태까지 반영하는 실제 구현을 그대로 쓴다.
        lenient().when(userTapProgressService.remainingTapsToNextPoint(any(), any(), any())).thenCallRealMethod();
        lenient().when(tapPolicyConfig.pointDailyCap()).thenReturn(150);
        lenient().when(tapPolicyConfig.maxPerDay()).thenReturn(3000);
    }

    @Test
    void returnsTodayCountsAndRemainingTapsToNextTargets() {
        AppUser user = mock(AppUser.class);
        when(userService.getById(userId)).thenReturn(user);

        UserTapDaily daily = UserTapDaily.createFor(user, today);
        // 실제 적립 경로는 두 카운트를 항상 함께 올린다.
        daily.addValidTaps(120);
        daily.addTotalValidTaps(120);
        daily.incrementPointEarned();
        when(userTapDailyService.getOrCreate(eq(user), eq(today))).thenReturn(daily);

        UserTapProgress progress = UserTapProgress.createFor(user, 300);
        progress.addValidTaps(120);
        when(userTapProgressService.getForUser(userId)).thenReturn(progress);

        UserTapSession session = UserTapSession.createFor(user, now, now.plusSeconds(3600), 200);
        session.addValidTaps(120);
        when(userTapSessionService.getOrCreateActiveSession(user, now, tapPolicyConfig)).thenReturn(session);

        TapTodayStatusResponse response = tapStatusService.getTodayStatus(userId);

        assertThat(response.date()).isEqualTo(today);
        assertThat(response.validTapCount()).isEqualTo(120);
        assertThat(response.pointEarnedToday()).isEqualTo(1);
        assertThat(response.remainingTapsToNextPoint()).isEqualTo(180);
        assertThat(response.remainingTapsToNextBox()).isEqualTo(80);
        assertThat(response.boxProgressTapCount()).isEqualTo(120);
        assertThat(response.nextBoxRequiredTapCount()).isEqualTo(200);
    }

    @Test
    void clampsRemainingTapsAtZeroWhenTargetAlreadyReached() {
        AppUser user = mock(AppUser.class);
        when(userService.getById(userId)).thenReturn(user);

        UserTapDaily daily = UserTapDaily.createFor(user, today);
        when(userTapDailyService.getOrCreate(eq(user), eq(today))).thenReturn(daily);

        UserTapProgress progress = UserTapProgress.createFor(user, 100);
        progress.addValidTaps(150);
        when(userTapProgressService.getForUser(userId)).thenReturn(progress);

        UserTapSession session = UserTapSession.createFor(user, now, now.plusSeconds(3600), 100);
        session.addValidTaps(150);
        when(userTapSessionService.getOrCreateActiveSession(user, now, tapPolicyConfig)).thenReturn(session);

        TapTodayStatusResponse response = tapStatusService.getTodayStatus(userId);

        assertThat(response.remainingTapsToNextPoint()).isZero();
        assertThat(response.remainingTapsToNextBox()).isZero();
        assertThat(response.boxProgressTapCount()).isEqualTo(150);
        assertThat(response.nextBoxRequiredTapCount()).isEqualTo(100);
    }

    @Test
    void reportsZeroRemainingTapsWhenNoMorePointsCanBeAwardedToday() {
        AppUser user = mock(AppUser.class);
        when(userService.getById(userId)).thenReturn(user);

        // 일일 탭 상한에 걸리면 진행도가 멈춰 목표와의 차이가 양수로 고정된다.
        UserTapDaily daily = UserTapDaily.createFor(user, today);
        daily.addValidTaps(3000);
        daily.addTotalValidTaps(3000);
        when(userTapDailyService.getOrCreate(eq(user), eq(today))).thenReturn(daily);

        UserTapProgress progress = UserTapProgress.createFor(user, 3010);
        progress.addValidTaps(3000);
        when(userTapProgressService.getForUser(userId)).thenReturn(progress);

        UserTapSession session = UserTapSession.createFor(user, now, now.plusSeconds(3600), 200);
        when(userTapSessionService.getOrCreateActiveSession(user, now, tapPolicyConfig)).thenReturn(session);

        TapTodayStatusResponse response = tapStatusService.getTodayStatus(userId);

        // 10탭 남은 것처럼 보이면 안 된다. 오늘은 더 지급되지 않는다.
        assertThat(response.remainingTapsToNextPoint()).isZero();
    }
}
