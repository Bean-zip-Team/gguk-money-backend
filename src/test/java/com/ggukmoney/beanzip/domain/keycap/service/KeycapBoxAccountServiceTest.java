package com.ggukmoney.beanzip.domain.keycap.service;

import com.ggukmoney.beanzip.domain.keycap.entity.KeycapBoxAccount;
import com.ggukmoney.beanzip.domain.keycap.repository.KeycapBoxAccountRepository;
import com.ggukmoney.beanzip.domain.user.entity.AppUser;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KeycapBoxAccountServiceTest {

    private final KeycapBoxAccountRepository keycapBoxAccountRepository = mock(KeycapBoxAccountRepository.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-07-16T00:00:00Z"), ZoneOffset.UTC);
    private final KeycapBoxAccountService keycapBoxAccountService =
            new KeycapBoxAccountService(keycapBoxAccountRepository, clock);

    private final UUID userId = UUID.randomUUID();

    @Test
    void createsAccountAtClockTimeAndSaves() {
        AppUser user = AppUser.createActive("Bean", null);
        when(keycapBoxAccountRepository.save(any(KeycapBoxAccount.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        KeycapBoxAccount result = keycapBoxAccountService.createFor(user);

        assertThat(result.getUser()).isSameAs(user);
        assertThat(result.getOpenCycleStartedAt()).isEqualTo(clock.instant());
        verify(keycapBoxAccountRepository).save(result);
    }

    @Test
    void refreshesOpenCycleFromLockedAccount() {
        KeycapBoxAccount account = KeycapBoxAccount.createFor(
                AppUser.createActive("Bean", null),
                clock.instant().minus(Duration.ofHours(2))
        );
        ReflectionTestUtils.setField(account, "freeOpenUsedCount", 2);
        ReflectionTestUtils.setField(account, "adOpenUsedCount", 2);
        when(keycapBoxAccountRepository.findByUserIdForUpdate(userId)).thenReturn(Optional.of(account));

        KeycapBoxAccount result = keycapBoxAccountService.refreshOpenCycleForUpdate(
                userId,
                clock.instant(),
                Duration.ofHours(1)
        );

        assertThat(result).isSameAs(account);
        assertThat(result.getOpenCycleStartedAt()).isEqualTo(clock.instant());
        assertThat(result.getFreeOpenUsedCount()).isZero();
        assertThat(result.getAdOpenUsedCount()).isZero();
        verify(keycapBoxAccountRepository).findByUserIdForUpdate(userId);
    }

    @Test
    void throwsNotFoundWhenAccountMissing() {
        when(keycapBoxAccountRepository.findByUserIdForUpdate(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> keycapBoxAccountService.getForUserForUpdate(userId))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("KEYCAP_BOX_ACCOUNT_NOT_FOUND");
    }
}
