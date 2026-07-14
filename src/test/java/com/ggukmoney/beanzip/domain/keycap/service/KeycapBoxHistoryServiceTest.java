package com.ggukmoney.beanzip.domain.keycap.service;

import com.ggukmoney.beanzip.domain.keycap.dto.mapper.KeycapBoxMapper;
import com.ggukmoney.beanzip.domain.keycap.dto.response.KeycapBoxHistoryItemResponse;
import com.ggukmoney.beanzip.domain.keycap.dto.response.KeycapBoxHistoryResponse;
import com.ggukmoney.beanzip.domain.keycap.entity.KeycapBoxOpen;
import com.ggukmoney.beanzip.domain.keycap.repository.KeycapBoxOpenRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.lang.reflect.Constructor;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KeycapBoxHistoryServiceTest {

    private final KeycapBoxOpenRepository keycapBoxOpenRepository = mock(KeycapBoxOpenRepository.class);
    private final KeycapBoxMapper keycapBoxMapper = mock(KeycapBoxMapper.class);
    private final KeycapBoxHistoryCursorCodec cursorCodec = new KeycapBoxHistoryCursorCodec();
    private final KeycapBoxHistoryService service = new KeycapBoxHistoryService(
            keycapBoxOpenRepository,
            keycapBoxMapper,
            cursorCodec
    );

    private final UUID userId = UUID.randomUUID();

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
    void usesDefaultSizeAndReturnsNextCursorWithSizePlusOneFetch() {
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
    void decodesCursorAndPassesCursorValuesToRepository() {
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
    void rejectsInvalidSize() {
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
