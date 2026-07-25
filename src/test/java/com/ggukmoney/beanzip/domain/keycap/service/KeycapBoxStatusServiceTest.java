package com.ggukmoney.beanzip.domain.keycap.service;

import com.ggukmoney.beanzip.domain.keycap.dto.mapper.KeycapBoxMapper;
import com.ggukmoney.beanzip.domain.keycap.dto.response.KeycapBoxStatusResponse;
import com.ggukmoney.beanzip.domain.keycap.entity.KeycapBoxAccount;
import com.ggukmoney.beanzip.domain.tap.dto.BoxProgressSnapshot;
import com.ggukmoney.beanzip.domain.tap.service.UserTapProgressService;
import com.ggukmoney.beanzip.domain.user.entity.AppUser;
import com.ggukmoney.beanzip.global.config.KeycapBoxPolicyConfig;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.lang.reflect.Constructor;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
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
    private final KeycapBoxPolicyConfig keycapBoxPolicyConfig = mock(KeycapBoxPolicyConfig.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-07-16T00:10:00Z"), ZoneOffset.UTC);
    private final KeycapBoxStatusService keycapBoxStatusService =
            new KeycapBoxStatusService(
                    keycapBoxAccountService,
                    userTapProgressService,
                    keycapBoxMapper,
                    keycapBoxPolicyConfig,
                    clock
            );

    @Test
    void getsStatusFromBoxAccountAndTapProgress() {
        UUID userId = UUID.randomUUID();
        KeycapBoxAccount account = keycapBoxAccount(userId, 2, 1, 0);
        BoxProgressSnapshot progress = new BoxProgressSnapshot(45, 100);
        KeycapBoxAccount.OpenCycleSnapshot snapshot =
                new KeycapBoxAccount.OpenCycleSnapshot(true, true, false, null);
        KeycapBoxStatusResponse mapped = new KeycapBoxStatusResponse(2, true, true, false, null, 45, 100);
        when(keycapBoxAccountService.getForUser(userId)).thenReturn(account);
        when(userTapProgressService.getBoxProgress(userId)).thenReturn(progress);
        when(keycapBoxPolicyConfig.openCycleDuration()).thenReturn(java.time.Duration.ofHours(1));
        when(keycapBoxPolicyConfig.freeOpenLimit()).thenReturn(2);
        when(keycapBoxPolicyConfig.adOpenLimit()).thenReturn(2);
        when(keycapBoxMapper.mapToStatusResponse(account, snapshot, progress)).thenReturn(mapped);

        KeycapBoxStatusResponse response = keycapBoxStatusService.getStatus(userId);

        assertThat(response).isEqualTo(mapped);
        verify(keycapBoxAccountService).getForUser(userId);
        verify(keycapBoxAccountService, never()).refillFreeTickets(userId);
        verify(userTapProgressService).getBoxProgress(userId);
        verify(keycapBoxMapper).mapToStatusResponse(account, snapshot, progress);
    }

    @Test
    void rejectsMissingBoxAccount() {
        UUID userId = UUID.randomUUID();
        when(keycapBoxAccountService.getForUser(userId))
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
        KeycapBoxAccount account = keycapBoxAccount(userId, 0, 0, 0);
        ResponseStatusException missingProgress =
                new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "TAP_PROGRESS_NOT_FOUND");
        when(keycapBoxAccountService.getForUser(userId)).thenReturn(account);
        when(keycapBoxPolicyConfig.openCycleDuration()).thenReturn(java.time.Duration.ofHours(1));
        when(keycapBoxPolicyConfig.freeOpenLimit()).thenReturn(2);
        when(keycapBoxPolicyConfig.adOpenLimit()).thenReturn(2);
        when(userTapProgressService.getBoxProgress(userId)).thenThrow(missingProgress);

        assertThatThrownBy(() -> keycapBoxStatusService.getStatus(userId))
                .isSameAs(missingProgress);
    }

    private static KeycapBoxAccount keycapBoxAccount(
            UUID userId,
            int boxBalance,
            int freeOpenUsedCount,
            int adOpenUsedCount
    ) {
        AppUser user = AppUser.createActive("Bean", null);
        ReflectionTestUtils.setField(user, "id", userId);

        KeycapBoxAccount account = newInstance(KeycapBoxAccount.class);
        ReflectionTestUtils.setField(account, "user", user);
        ReflectionTestUtils.setField(account, "boxBalance", boxBalance);
        ReflectionTestUtils.setField(account, "freeOpenUsedCount", freeOpenUsedCount);
        ReflectionTestUtils.setField(account, "adOpenUsedCount", adOpenUsedCount);
        ReflectionTestUtils.setField(account, "openCycleStartedAt", Instant.parse("2026-07-16T00:00:00Z"));
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
