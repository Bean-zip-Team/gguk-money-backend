package com.ggukmoney.beanzip.domain.keycap.service;

import com.ggukmoney.beanzip.domain.keycap.entity.KeycapBoxAccount;
import com.ggukmoney.beanzip.domain.keycap.repository.KeycapBoxAccountRepository;
import com.ggukmoney.beanzip.domain.user.entity.AppUser;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

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
    private final KeycapBoxAccountService keycapBoxAccountService = new KeycapBoxAccountService(keycapBoxAccountRepository);

    @Test
    void createsEmptyWalletForNewUser() {
        AppUser user = AppUser.createActive("Bean", null);
        when(keycapBoxAccountRepository.save(any(KeycapBoxAccount.class))).thenAnswer(invocation -> invocation.getArgument(0));

        KeycapBoxAccount result = keycapBoxAccountService.createFor(user);

        assertThat(result.getUser()).isSameAs(user);
        assertThat(result.getShardBalance()).isZero();
        verify(keycapBoxAccountRepository).save(result);
    }

    @Test
    void locksWalletRowForUpdate() {
        UUID userId = UUID.randomUUID();
        KeycapBoxAccount wallet = KeycapBoxAccount.createFor(null);
        when(keycapBoxAccountRepository.findByUserIdForUpdate(userId)).thenReturn(Optional.of(wallet));

        assertThat(keycapBoxAccountService.getForUserForUpdate(userId)).isSameAs(wallet);
        verify(keycapBoxAccountRepository).findByUserIdForUpdate(userId);
    }

    @Test
    void missingWalletIsNotFound() {
        UUID userId = UUID.randomUUID();
        when(keycapBoxAccountRepository.findByUserId(userId)).thenReturn(Optional.empty());
        when(keycapBoxAccountRepository.findByUserIdForUpdate(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> keycapBoxAccountService.getForUser(userId))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(exception -> ((ResponseStatusException) exception).getReason())
                .isEqualTo("KEYCAP_BOX_ACCOUNT_NOT_FOUND");
        assertThatThrownBy(() -> keycapBoxAccountService.getForUserForUpdate(userId))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(exception -> ((ResponseStatusException) exception).getReason())
                .isEqualTo("KEYCAP_BOX_ACCOUNT_NOT_FOUND");
    }
}
