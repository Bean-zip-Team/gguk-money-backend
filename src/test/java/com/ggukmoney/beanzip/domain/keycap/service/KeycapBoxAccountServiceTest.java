package com.ggukmoney.beanzip.domain.keycap.service;

import com.ggukmoney.beanzip.domain.keycap.entity.KeycapBoxAccount;
import com.ggukmoney.beanzip.domain.keycap.repository.KeycapBoxAccountRepository;
import com.ggukmoney.beanzip.global.config.KeycapBoxPolicyConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.lang.reflect.Constructor;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KeycapBoxAccountServiceTest {

    private final KeycapBoxAccountRepository keycapBoxAccountRepository = mock(KeycapBoxAccountRepository.class);
    private final KeycapBoxPolicyConfig keycapBoxPolicyConfig = mock(KeycapBoxPolicyConfig.class);
    private final KeycapBoxAccountService keycapBoxAccountService =
            new KeycapBoxAccountService(keycapBoxAccountRepository, keycapBoxPolicyConfig);

    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void stubPolicyDefaults() {
        when(keycapBoxPolicyConfig.refillPerHour()).thenReturn(1);
        when(keycapBoxPolicyConfig.cap()).thenReturn(8);
    }

    @Test
    void refillsElapsedFreeTicketsAndSaves() {
        Instant threeHoursAgo = Instant.now().minusSeconds(3 * 3600);
        KeycapBoxAccount account = accountWithTicket(0, threeHoursAgo);
        when(keycapBoxAccountRepository.findByUserIdForUpdate(userId)).thenReturn(Optional.of(account));
        when(keycapBoxAccountRepository.save(account)).thenReturn(account);

        KeycapBoxAccount result = keycapBoxAccountService.refillFreeTickets(userId);

        assertThat(result.getFreeOpenTicketCount()).isEqualTo(3);
        verify(keycapBoxAccountRepository).save(account);
    }

    @Test
    void throwsNotFoundWhenAccountMissing() {
        when(keycapBoxAccountRepository.findByUserIdForUpdate(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> keycapBoxAccountService.refillFreeTickets(userId))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("KEYCAP_BOX_ACCOUNT_NOT_FOUND");
    }

    private static KeycapBoxAccount accountWithTicket(int freeOpenTicketCount, Instant lastGrantedAt) {
        KeycapBoxAccount account = newInstance(KeycapBoxAccount.class);
        ReflectionTestUtils.setField(account, "freeOpenTicketCount", freeOpenTicketCount);
        ReflectionTestUtils.setField(account, "lastFreeTicketGrantedAt", lastGrantedAt);
        return account;
    }

    private static <T> T newInstance(Class<T> type) {
        try {
            Constructor<T> constructor = type.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Failed to create test entity " + type.getSimpleName(), exception);
        }
    }
}
