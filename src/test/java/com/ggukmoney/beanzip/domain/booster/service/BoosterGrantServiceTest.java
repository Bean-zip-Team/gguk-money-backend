package com.ggukmoney.beanzip.domain.booster.service;

import com.ggukmoney.beanzip.domain.booster.dto.response.BoosterActivateResponse;
import com.ggukmoney.beanzip.domain.booster.dto.response.BoosterStatusResponse;
import com.ggukmoney.beanzip.domain.booster.entity.BoosterGrant;
import com.ggukmoney.beanzip.domain.booster.repository.BoosterGrantRepository;
import com.ggukmoney.beanzip.domain.user.entity.AppUser;
import com.ggukmoney.beanzip.domain.user.service.UserService;
import com.ggukmoney.beanzip.global.config.TapPolicyConfig;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BoosterGrantServiceTest {

    private final BoosterGrantRepository boosterGrantRepository = mock(BoosterGrantRepository.class);
    private final UserService userService = mock(UserService.class);
    private final TapPolicyConfig tapPolicyConfig = mock(TapPolicyConfig.class);
    private final BoosterGrantService boosterGrantService =
            new BoosterGrantService(boosterGrantRepository, userService, tapPolicyConfig);

    private final UUID userId = UUID.randomUUID();

    @Test
    void activatesWhenNoActiveGrantAndUnderDailyLimit() {
        when(boosterGrantRepository.findByUserIdAndStatusAndExpiresAtAfter(eq(userId), eq(BoosterGrant.Status.ACTIVE), any(Instant.class)))
                .thenReturn(Optional.empty());
        when(tapPolicyConfig.boosterDailyLimit()).thenReturn(3);
        when(boosterGrantRepository.countByUserIdAndGrantDate(eq(userId), any(LocalDate.class))).thenReturn(1L);
        when(tapPolicyConfig.boosterDurationSeconds()).thenReturn(300);
        AppUser user = mock(AppUser.class);
        when(userService.getById(userId)).thenReturn(user);
        when(boosterGrantRepository.save(any(BoosterGrant.class))).thenAnswer(invocation -> invocation.getArgument(0));

        BoosterActivateResponse response = boosterGrantService.activate(userId, UUID.randomUUID());

        assertThat(response.active()).isTrue();
        assertThat(response.multiplier()).isEqualByComparingTo("2.0");
        assertThat(response.remainingDailyCount()).isEqualTo(1);
        assertThat(response.startsAt()).isBeforeOrEqualTo(response.endsAt());
    }

    @Test
    void throwsWhenAdViewIdMissing() {
        assertThatThrownBy(() -> boosterGrantService.activate(userId, null))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("AD_VIEW_ID_REQUIRED");
    }

    @Test
    void throwsWhenAlreadyActive() {
        BoosterGrant activeGrant = mock(BoosterGrant.class);
        when(boosterGrantRepository.findByUserIdAndStatusAndExpiresAtAfter(eq(userId), eq(BoosterGrant.Status.ACTIVE), any(Instant.class)))
                .thenReturn(Optional.of(activeGrant));

        assertThatThrownBy(() -> boosterGrantService.activate(userId, UUID.randomUUID()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("BOOSTER_ALREADY_ACTIVE");
    }

    @Test
    void throwsWhenDailyLimitReached() {
        when(boosterGrantRepository.findByUserIdAndStatusAndExpiresAtAfter(eq(userId), eq(BoosterGrant.Status.ACTIVE), any(Instant.class)))
                .thenReturn(Optional.empty());
        when(tapPolicyConfig.boosterDailyLimit()).thenReturn(3);
        when(boosterGrantRepository.countByUserIdAndGrantDate(eq(userId), any(LocalDate.class))).thenReturn(3L);

        assertThatThrownBy(() -> boosterGrantService.activate(userId, UUID.randomUUID()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("BOOSTER_DAILY_LIMIT_EXCEEDED");
    }

    @Test
    void currentStatusReflectsActiveGrant() {
        AppUser user = mock(AppUser.class);
        BoosterGrant grant = BoosterGrant.activate(user, LocalDate.now(), 1, Duration.ofSeconds(300));
        when(boosterGrantRepository.findByUserIdAndStatusAndExpiresAtAfter(eq(userId), eq(BoosterGrant.Status.ACTIVE), any(Instant.class)))
                .thenReturn(Optional.of(grant));
        when(tapPolicyConfig.boosterDailyLimit()).thenReturn(3);
        when(boosterGrantRepository.countByUserIdAndGrantDate(eq(userId), any(LocalDate.class))).thenReturn(1L);

        BoosterStatusResponse status = boosterGrantService.getCurrentStatus(userId);

        assertThat(status.active()).isTrue();
        assertThat(status.multiplier()).isEqualByComparingTo("2.0");
        assertThat(status.remainingSeconds()).isGreaterThan(0);
        assertThat(status.remainingDailyCount()).isEqualTo(2);
    }

    @Test
    void currentStatusReflectsNoActiveGrant() {
        when(boosterGrantRepository.findByUserIdAndStatusAndExpiresAtAfter(eq(userId), eq(BoosterGrant.Status.ACTIVE), any(Instant.class)))
                .thenReturn(Optional.empty());
        when(tapPolicyConfig.boosterDailyLimit()).thenReturn(3);
        when(boosterGrantRepository.countByUserIdAndGrantDate(eq(userId), any(LocalDate.class))).thenReturn(0L);

        BoosterStatusResponse status = boosterGrantService.getCurrentStatus(userId);

        assertThat(status.active()).isFalse();
        assertThat(status.multiplier()).isEqualByComparingTo(BigDecimal.ONE);
        assertThat(status.remainingDailyCount()).isEqualTo(3);
    }

    @Test
    void findActiveMultiplierReturnsGrantMultiplierWhenActive() {
        AppUser user = mock(AppUser.class);
        BoosterGrant grant = BoosterGrant.activate(user, LocalDate.now(), 1, Duration.ofSeconds(300));
        Instant now = Instant.now();
        when(boosterGrantRepository.findByUserIdAndStatusAndExpiresAtAfter(userId, BoosterGrant.Status.ACTIVE, now))
                .thenReturn(Optional.of(grant));

        assertThat(boosterGrantService.findActiveMultiplier(userId, now)).isEqualByComparingTo("2.0");
    }

    @Test
    void findActiveMultiplierReturnsOneWhenNoActiveGrant() {
        Instant now = Instant.now();
        when(boosterGrantRepository.findByUserIdAndStatusAndExpiresAtAfter(userId, BoosterGrant.Status.ACTIVE, now))
                .thenReturn(Optional.empty());

        assertThat(boosterGrantService.findActiveMultiplier(userId, now)).isEqualByComparingTo(BigDecimal.ONE);
    }
}
