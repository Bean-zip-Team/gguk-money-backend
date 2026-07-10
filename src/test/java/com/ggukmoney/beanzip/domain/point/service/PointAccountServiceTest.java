package com.ggukmoney.beanzip.domain.point.service;

import com.ggukmoney.beanzip.domain.point.entity.PointAccount;
import com.ggukmoney.beanzip.domain.point.repository.PointAccountRepository;
import com.ggukmoney.beanzip.domain.user.entity.AppUser;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PointAccountServiceTest {

    private final PointAccountRepository pointAccountRepository = mock(PointAccountRepository.class);
    private final PointAccountService pointAccountService = new PointAccountService(pointAccountRepository);

    @Test
    void creditsBalanceAndSavesAccount() {
        UUID userId = UUID.randomUUID();
        PointAccount account = PointAccount.createFor(mock(AppUser.class));
        when(pointAccountRepository.findByUserId(userId)).thenReturn(Optional.of(account));
        when(pointAccountRepository.save(account)).thenReturn(account);

        PointAccount result = pointAccountService.credit(userId, 5);

        assertThat(result.getBalance()).isEqualTo(5L);
        assertThat(result.getLifetimeEarned()).isEqualTo(5L);
        verify(pointAccountRepository).save(account);
    }

    @Test
    void throwsNotFoundWhenAccountMissing() {
        UUID userId = UUID.randomUUID();
        when(pointAccountRepository.findByUserId(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> pointAccountService.getBalance(userId))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("POINT_ACCOUNT_NOT_FOUND");
    }
}
