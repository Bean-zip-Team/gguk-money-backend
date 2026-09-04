package com.ggukmoney.beanzip.domain.tap.service;

import com.ggukmoney.beanzip.domain.tap.entity.UserTapDaily;
import com.ggukmoney.beanzip.domain.tap.repository.UserTapDailyRepository;
import com.ggukmoney.beanzip.domain.user.entity.AppUser;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserTapDailyServiceTest {

    private final UserTapDailyRepository userTapDailyRepository = mock(UserTapDailyRepository.class);
    private final UserTapDailyService userTapDailyService = new UserTapDailyService(userTapDailyRepository);

    @Test
    void returnsExistingRowWithoutCreatingWhenAlreadyPresent() {
        UUID userId = UUID.randomUUID();
        LocalDate tapDate = LocalDate.of(2026, 7, 21);
        AppUser user = mock(AppUser.class);
        when(user.getId()).thenReturn(userId);
        UserTapDaily existing = mock(UserTapDaily.class);
        when(userTapDailyRepository.findByUserIdAndTapDate(userId, tapDate))
                .thenReturn(Optional.of(existing));

        UserTapDaily result = userTapDailyService.getOrCreate(user, tapDate);

        assertThat(result).isEqualTo(existing);
        verify(userTapDailyRepository).findByUserIdAndTapDate(userId, tapDate);
    }

    @Test
    void createsNewRowWithZeroCountsWhenMissing() {
        UUID userId = UUID.randomUUID();
        LocalDate tapDate = LocalDate.of(2026, 7, 21);
        AppUser user = mock(AppUser.class);
        when(user.getId()).thenReturn(userId);
        when(userTapDailyRepository.findByUserIdAndTapDate(userId, tapDate))
                .thenReturn(Optional.empty());
        when(userTapDailyRepository.save(any(UserTapDaily.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserTapDaily result = userTapDailyService.getOrCreate(user, tapDate);

        assertThat(result.getTapDate()).isEqualTo(tapDate);
        assertThat(result.getValidTapCount()).isZero();
        assertThat(result.getPointEarnedAmount()).isZero();
    }
}
