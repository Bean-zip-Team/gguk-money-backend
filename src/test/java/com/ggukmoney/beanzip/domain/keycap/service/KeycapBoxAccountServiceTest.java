package com.ggukmoney.beanzip.domain.keycap.service;

import com.ggukmoney.beanzip.domain.keycap.entity.KeycapBoxAccount;
import com.ggukmoney.beanzip.domain.keycap.repository.KeycapBoxAccountRepository;
import com.ggukmoney.beanzip.domain.user.entity.AppUser;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
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

    private final Clock clock = Clock.fixed(Instant.parse("2026-07-16T00:00:00Z"), ZoneOffset.UTC);

    private final KeycapBoxAccountRepository keycapBoxAccountRepository = mock(KeycapBoxAccountRepository.class);
    private final com.ggukmoney.beanzip.domain.keycap.repository.UserKeycapRepository userKeycaps = mock(com.ggukmoney.beanzip.domain.keycap.repository.UserKeycapRepository.class);
    private final KeycapBoxAccountService keycapBoxAccountService = new KeycapBoxAccountService(keycapBoxAccountRepository, clock, userKeycaps, org.mockito.Mockito.mock(com.ggukmoney.beanzip.global.config.KeycapBoxPolicyConfig.class));

    @Test
    void createsEmptyWalletForNewUser() {
        AppUser user = AppUser.createActive("Bean", null);
        when(keycapBoxAccountRepository.save(any(KeycapBoxAccount.class))).thenAnswer(invocation -> invocation.getArgument(0));

        KeycapBoxAccount result = keycapBoxAccountService.createFor(user);

        assertThat(result.getUser()).isSameAs(user);
        assertThat(result.getShardBalance()).isZero();
        assertThat(result.getOpenCycleStartedAt()).isEqualTo(clock.instant());
        verify(keycapBoxAccountRepository).save(result);
    }

    @Test
    void locksWalletRowForUpdate() {
        UUID userId = UUID.randomUUID();
        KeycapBoxAccount wallet = KeycapBoxAccount.createFor(null, clock.instant());
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

    @Test
    void legacyTapDoesNotConvertOrDeletePerKeycapProgress() {
        UUID userId = UUID.randomUUID();
        KeycapBoxAccount wallet = KeycapBoxAccount.createFor(null, clock.instant());
        wallet.addBoxes(7);
        var collecting = com.ggukmoney.beanzip.domain.keycap.entity.UserKeycap.createInProgress(null, null);
        org.springframework.test.util.ReflectionTestUtils.setField(collecting, "shardCount", 3);
        when(keycapBoxAccountRepository.findByUserIdForUpdate(userId)).thenReturn(Optional.of(wallet));
        org.mockito.Mockito.lenient().when(userKeycaps.findLegacyInProgressForUpdate(userId))
                .thenReturn(java.util.List.of(collecting));

        var result = keycapBoxAccountService.getForUserForUpdate(userId);

        assertThat(result.getBoxBalance()).isEqualTo(7);
        assertThat(collecting.getShardCount()).isEqualTo(3);
        verify(userKeycaps, org.mockito.Mockito.never()).deleteAll(any());
    }
    @Test void rawWalletLockDoesNotReadEconomicPolicyOrLegacyProgress() {
        UUID userId=UUID.randomUUID();
        var policy=mock(com.ggukmoney.beanzip.global.config.KeycapBoxPolicyConfig.class);
        var service=new KeycapBoxAccountService(keycapBoxAccountRepository,clock,userKeycaps,policy);
        when(keycapBoxAccountRepository.findByUserIdForUpdate(userId)).thenReturn(Optional.of(KeycapBoxAccount.createFor(null, clock.instant())));
        service.getForUserForUpdate(userId);
        org.mockito.Mockito.verifyNoInteractions(policy,userKeycaps);
    }
}
