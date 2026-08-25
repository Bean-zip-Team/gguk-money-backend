package com.ggukmoney.beanzip.domain.keycap.service;

import com.ggukmoney.beanzip.domain.keycap.dto.mapper.KeycapBoxMapper;
import com.ggukmoney.beanzip.domain.keycap.dto.response.KeycapBoxHistoryItemResponse;
import com.ggukmoney.beanzip.domain.keycap.dto.response.KeycapBoxHistoryResponse;
import com.ggukmoney.beanzip.domain.keycap.dto.response.KeycapBoxStatusResponse;
import com.ggukmoney.beanzip.domain.keycap.entity.KeycapBoxAccount;
import com.ggukmoney.beanzip.domain.keycap.entity.KeycapBoxOpen;
import com.ggukmoney.beanzip.domain.keycap.repository.KeycapBoxOpenRepository;
import com.ggukmoney.beanzip.domain.tap.dto.BoxProgressSnapshot;
import com.ggukmoney.beanzip.domain.tap.service.UserTapSessionService;
import com.ggukmoney.beanzip.domain.user.entity.AppUser;
import com.ggukmoney.beanzip.domain.user.service.UserService;
import com.ggukmoney.beanzip.global.config.KeycapBoxPolicyConfig;
import com.ggukmoney.beanzip.global.config.TapPolicyConfig;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.lang.reflect.Constructor;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KeycapBoxQueryServiceTest {

    private final KeycapBoxAccountService keycapBoxAccountService = mock(KeycapBoxAccountService.class);
    private final UserTapSessionService userTapSessionService = mock(UserTapSessionService.class);
    private final UserService userService = mock(UserService.class);
    private final TapPolicyConfig tapPolicyConfig = mock(TapPolicyConfig.class);
    private final KeycapBoxMapper keycapBoxMapper = mock(KeycapBoxMapper.class);
    private final KeycapBoxPolicyConfig keycapBoxPolicyConfig = mock(KeycapBoxPolicyConfig.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-07-16T00:10:00Z"), ZoneOffset.UTC);
    private final KeycapBoxOpenRepository keycapBoxOpenRepository = mock(KeycapBoxOpenRepository.class);
    private final KeycapBoxHistoryCursorCodec cursorCodec = new KeycapBoxHistoryCursorCodec();
    private final KeycapBoxQueryService service = new KeycapBoxQueryService(
            keycapBoxAccountService,
            userTapSessionService,
            userService,
            tapPolicyConfig,
            keycapBoxMapper,
            keycapBoxPolicyConfig,
            clock,
            keycapBoxOpenRepository,
            cursorCodec
    );

    private final UUID userId = UUID.randomUUID();

    @Test
    void getsStatusFromOrdinaryAccountLookupAndTapProgress() {
        KeycapBoxAccount account = keycapBoxAccount(userId, 2, 1, 0);
        AppUser user = account.getUser();
        when(userService.getById(userId)).thenReturn(user);
        BoxProgressSnapshot progress = new BoxProgressSnapshot(45, 100);
        KeycapBoxAccount.OpenCycleSnapshot snapshot =
                new KeycapBoxAccount.OpenCycleSnapshot(true, true, false, null);
        KeycapBoxStatusResponse mapped = new KeycapBoxStatusResponse(2, true, true, false, null, 45, 100);
        when(keycapBoxAccountService.getForUser(userId)).thenReturn(account);
        when(userTapSessionService.getBoxProgress(user, clock.instant(), tapPolicyConfig)).thenReturn(progress);
        when(keycapBoxPolicyConfig.openCycleDuration()).thenReturn(java.time.Duration.ofHours(1));
        when(keycapBoxPolicyConfig.freeOpenLimit()).thenReturn(2);
        when(keycapBoxPolicyConfig.adOpenLimit()).thenReturn(2);
        when(keycapBoxMapper.mapToStatusResponse(account, snapshot, progress)).thenReturn(mapped);

        KeycapBoxStatusResponse response = service.getStatus(userId);

        assertThat(response).isEqualTo(mapped);
        verify(keycapBoxAccountService).getForUser(userId);
        verify(userTapSessionService).getBoxProgress(user, clock.instant(), tapPolicyConfig);
        verify(keycapBoxMapper).mapToStatusResponse(account, snapshot, progress);
    }

    @Test
    void propagatesMissingBoxAccountWithoutLoadingTapProgress() {
        when(keycapBoxAccountService.getForUser(userId))
                .thenThrow(new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "KEYCAP_BOX_ACCOUNT_NOT_FOUND"));

        assertThatThrownBy(() -> service.getStatus(userId))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(exception -> ((ResponseStatusException) exception).getReason())
                .isEqualTo("KEYCAP_BOX_ACCOUNT_NOT_FOUND");

        verify(userTapSessionService, never()).getBoxProgress(any(), any(), any());
    }

    @Test
    void returnsEmptyHistoryWithoutCursorWhenUserHasNoBoxOpens() {
        when(keycapBoxOpenRepository.findHistoryByUserId(userId, null, null, Pageable.ofSize(21)))
                .thenReturn(List.of());

        KeycapBoxHistoryResponse response = service.getHistory(userId, null, null);

        assertThat(response.content()).isEmpty();
        assertThat(response.nextCursor()).isNull();
        assertThat(response.hasNext()).isFalse();
        verify(keycapBoxOpenRepository, never()).save(any());
    }

    @Test
    void returnsNextCursorFromLastReturnedItemAfterSizePlusOneFetch() {
        Instant firstOpenedAt = Instant.parse("2026-07-15T01:00:00Z");
        Instant secondOpenedAt = Instant.parse("2026-07-15T00:00:00Z");
        KeycapBoxOpen first = open(20L, firstOpenedAt);
        KeycapBoxOpen second = open(19L, secondOpenedAt);
        KeycapBoxOpen extra = open(18L, Instant.parse("2026-07-14T23:00:00Z"));
        KeycapBoxHistoryItemResponse firstItem = item(firstOpenedAt);
        KeycapBoxHistoryItemResponse secondItem = item(secondOpenedAt);
        when(keycapBoxOpenRepository.findHistoryByUserId(userId, null, null, Pageable.ofSize(3)))
                .thenReturn(List.of(first, second, extra));
        when(keycapBoxMapper.mapToHistoryItemResponse(first)).thenReturn(firstItem);
        when(keycapBoxMapper.mapToHistoryItemResponse(second)).thenReturn(secondItem);

        KeycapBoxHistoryResponse response = service.getHistory(userId, null, 2);

        assertThat(response.content()).containsExactly(firstItem, secondItem);
        assertThat(response.hasNext()).isTrue();
        assertThat(cursorCodec.decode(response.nextCursor()).openedAt()).isEqualTo(secondOpenedAt);
        assertThat(cursorCodec.decode(response.nextCursor()).id()).isEqualTo(19L);
    }

    @Test
    void decodesCursorAndPassesCursorValuesToHistoryRepository() {
        Instant openedAt = Instant.parse("2026-07-15T00:00:00Z");
        String cursor = cursorCodec.encode(openedAt, 15L);
        ArgumentCaptor<Instant> openedAtCaptor = ArgumentCaptor.forClass(Instant.class);
        ArgumentCaptor<Long> idCaptor = ArgumentCaptor.forClass(Long.class);
        when(keycapBoxOpenRepository.findHistoryByUserId(
                org.mockito.ArgumentMatchers.eq(userId),
                openedAtCaptor.capture(),
                idCaptor.capture(),
                any(Pageable.class)
        )).thenReturn(List.of());

        service.getHistory(userId, cursor, 20);

        assertThat(openedAtCaptor.getValue()).isEqualTo(openedAt);
        assertThat(idCaptor.getValue()).isEqualTo(15L);
    }

    @Test
    void rejectsHistorySizeOutsideAllowedRange() {
        assertThatThrownBy(() -> service.getHistory(userId, null, 0))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(exception -> ((ResponseStatusException) exception).getReason())
                .isEqualTo("COMMON_VALIDATION_ERROR");

        assertThatThrownBy(() -> service.getHistory(userId, null, 101))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(exception -> ((ResponseStatusException) exception).getReason())
                .isEqualTo("COMMON_VALIDATION_ERROR");
    }

    private static KeycapBoxHistoryItemResponse item(Instant openedAt) {
        return new KeycapBoxHistoryItemResponse(UUID.randomUUID(), "FREE", UUID.randomUUID(), 1, false, openedAt);
    }

    private static KeycapBoxOpen open(Long id, Instant openedAt) {
        KeycapBoxOpen open = newInstance(KeycapBoxOpen.class);
        ReflectionTestUtils.setField(open, "id", id);
        ReflectionTestUtils.setField(open, "openedAt", openedAt);
        return open;
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
