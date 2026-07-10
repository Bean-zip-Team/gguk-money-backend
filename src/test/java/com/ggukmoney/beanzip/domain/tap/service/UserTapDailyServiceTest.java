package com.ggukmoney.beanzip.domain.tap.service;

import com.ggukmoney.beanzip.domain.tap.config.TapPolicyConfig;
import com.ggukmoney.beanzip.domain.tap.entity.UserTapDaily;
import com.ggukmoney.beanzip.domain.tap.repository.UserTapDailyRepository;
import com.ggukmoney.beanzip.domain.user.entity.AppUser;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserTapDailyServiceTest {

    private final UserTapDailyRepository userTapDailyRepository = mock(UserTapDailyRepository.class);
    private final UserTapDailyService userTapDailyService = new UserTapDailyService(userTapDailyRepository);

    @Test
    void returnsExistingRowWithoutDrawingNewTargetWhenAlreadyPresent() {
        UUID userId = UUID.randomUUID();
        AppUser user = mock(AppUser.class);
        when(user.getId()).thenReturn(userId);
        UserTapDaily existing = mock(UserTapDaily.class);
        when(userTapDailyRepository.findByUserIdAndTapDate(eq(userId), any(LocalDate.class)))
                .thenReturn(Optional.of(existing));
        TapCurveCalculator curveCalculator = mock(TapCurveCalculator.class);
        TapPolicyConfig config = mock(TapPolicyConfig.class);

        UserTapDaily result = userTapDailyService.getOrCreateToday(user, curveCalculator, config);

        assertThat(result).isEqualTo(existing);
        verify(curveCalculator, never()).drawNextTarget(any(Integer.class), any(Integer.class), any());
    }

    @Test
    void createsNewRowWithFreshlyDrawnTargetWhenMissing() {
        UUID userId = UUID.randomUUID();
        AppUser user = mock(AppUser.class);
        when(user.getId()).thenReturn(userId);
        when(userTapDailyRepository.findByUserIdAndTapDate(eq(userId), any(LocalDate.class)))
                .thenReturn(Optional.empty());
        TapCurveCalculator curveCalculator = mock(TapCurveCalculator.class);
        TapPolicyConfig config = mock(TapPolicyConfig.class);
        when(curveCalculator.drawNextTarget(0, 0, config)).thenReturn(287);
        when(userTapDailyRepository.save(any(UserTapDaily.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserTapDaily result = userTapDailyService.getOrCreateToday(user, curveCalculator, config);

        assertThat(result.getNextPointTarget()).isEqualTo(287);
    }
}
