package com.ggukmoney.beanzip.domain.keycap.service;

import com.ggukmoney.beanzip.domain.keycap.dto.mapper.KeycapBoxMapper;
import com.ggukmoney.beanzip.domain.keycap.dto.response.KeycapBoxStatusResponse;
import com.ggukmoney.beanzip.domain.keycap.entity.KeycapBoxAccount;
import com.ggukmoney.beanzip.domain.tap.dto.BoxProgressSnapshot;
import com.ggukmoney.beanzip.domain.tap.service.UserTapProgressService;
import com.ggukmoney.beanzip.domain.user.entity.AppUser;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.lang.reflect.Constructor;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KeycapBoxStatusServiceTest {

    private final KeycapBoxAccountService keycapBoxAccountService = mock(KeycapBoxAccountService.class);
    private final UserTapProgressService userTapProgressService = mock(UserTapProgressService.class);
    private final KeycapBoxMapper keycapBoxMapper = mock(KeycapBoxMapper.class);
    private final KeycapBoxStatusService keycapBoxStatusService =
            new KeycapBoxStatusService(keycapBoxAccountService, userTapProgressService, keycapBoxMapper);

    @Test
    void getsStatusFromBoxAccountAndTapProgress() {
        UUID userId = UUID.randomUUID();
        KeycapBoxAccount account = keycapBoxAccount(userId, 2, 1);
        BoxProgressSnapshot progress = new BoxProgressSnapshot(45, 100);
        KeycapBoxStatusResponse mapped = new KeycapBoxStatusResponse(2, 1, 45, 100);
        when(keycapBoxAccountService.refillFreeTickets(userId)).thenReturn(account);
        when(userTapProgressService.getBoxProgress(userId)).thenReturn(progress);
        when(keycapBoxMapper.mapToStatusResponse(account, progress)).thenReturn(mapped);

        KeycapBoxStatusResponse response = keycapBoxStatusService.getStatus(userId);

        assertThat(response).isEqualTo(mapped);
        verify(keycapBoxAccountService).refillFreeTickets(userId);
        verify(userTapProgressService).getBoxProgress(userId);
        verify(keycapBoxMapper).mapToStatusResponse(account, progress);
    }

    @Test
    void rejectsMissingBoxAccount() {
        UUID userId = UUID.randomUUID();
        when(keycapBoxAccountService.refillFreeTickets(userId))
                .thenThrow(new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "KEYCAP_BOX_ACCOUNT_NOT_FOUND"));

        assertThatThrownBy(() -> keycapBoxStatusService.getStatus(userId))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(exception -> ((ResponseStatusException) exception).getReason())
                .isEqualTo("KEYCAP_BOX_ACCOUNT_NOT_FOUND");

        verify(userTapProgressService, never()).getBoxProgress(userId);
    }

    @Test
    void propagatesMissingTapProgressPolicy() {
        UUID userId = UUID.randomUUID();
        KeycapBoxAccount account = keycapBoxAccount(userId, 0, 0);
        ResponseStatusException missingProgress =
                new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "TAP_PROGRESS_NOT_FOUND");
        when(keycapBoxAccountService.refillFreeTickets(userId)).thenReturn(account);
        when(userTapProgressService.getBoxProgress(userId)).thenThrow(missingProgress);

        assertThatThrownBy(() -> keycapBoxStatusService.getStatus(userId))
                .isSameAs(missingProgress);
    }

    private static KeycapBoxAccount keycapBoxAccount(UUID userId, int boxBalance, int freeOpenTicketCount) {
        AppUser user = AppUser.createActive("Bean", null);
        ReflectionTestUtils.setField(user, "id", userId);

        KeycapBoxAccount account = newInstance(KeycapBoxAccount.class);
        ReflectionTestUtils.setField(account, "user", user);
        ReflectionTestUtils.setField(account, "boxBalance", boxBalance);
        ReflectionTestUtils.setField(account, "freeOpenTicketCount", freeOpenTicketCount);
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
