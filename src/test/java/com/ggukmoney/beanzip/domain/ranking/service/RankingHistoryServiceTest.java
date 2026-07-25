package com.ggukmoney.beanzip.domain.ranking.service;

import com.ggukmoney.beanzip.domain.ranking.dto.response.RankingHistoryResponse;
import com.ggukmoney.beanzip.domain.ranking.repository.RankingEntryRepository;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RankingHistoryServiceTest {

    private final RankingEntryRepository entryRepository = mock(RankingEntryRepository.class);
    private final RankingHistoryCursorCodec cursorCodec = new RankingHistoryCursorCodec();
    private final RankingHistoryService service = new RankingHistoryService(entryRepository, cursorCodec);

    @Test
    void usesDefaultSizeAndReturnsNextCursorFromLastReturnedItem() {
        UUID userId = UUID.randomUUID();
        List<RankingEntryRepository.RankingHistoryRow> rows = java.util.stream.LongStream.rangeClosed(1, 21)
                .mapToObj(index -> row(index, Instant.parse("2026-07-26T15:00:00Z").minusSeconds(index), index))
                .toList();
        when(entryRepository.findWeeklyHistory(userId, null, null, 21)).thenReturn(rows);

        RankingHistoryResponse response = service.getHistory(userId, null, null);

        assertThat(response.content()).hasSize(20);
        assertThat(response.hasNext()).isTrue();
        RankingHistoryCursorCodec.Cursor cursor = cursorCodec.decode(response.nextCursor());
        assertThat(cursor.endsAt()).isEqualTo(response.content().get(19).endsAt());
        assertThat(cursor.seasonId()).isEqualTo(20L);
    }

    @Test
    void acceptsSizeOneHundredAndReturnsEmptyPageWithoutCursor() {
        UUID userId = UUID.randomUUID();
        when(entryRepository.findWeeklyHistory(userId, null, null, 101)).thenReturn(List.of());

        RankingHistoryResponse response = service.getHistory(userId, " ", 100);

        assertThat(response.content()).isEmpty();
        assertThat(response.nextCursor()).isNull();
        assertThat(response.hasNext()).isFalse();
    }

    @Test
    void rejectsInvalidSize() {
        UUID userId = UUID.randomUUID();

        assertThatThrownBy(() -> service.getHistory(userId, null, 0))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("COMMON_VALIDATION_ERROR");
        assertThatThrownBy(() -> service.getHistory(userId, null, 101))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("COMMON_VALIDATION_ERROR");
    }

    @Test
    void decodesCursorAndPassesCursorValuesToRepository() {
        UUID userId = UUID.randomUUID();
        Instant cursorEndsAt = Instant.parse("2026-07-26T15:00:00Z");
        String cursor = cursorCodec.encode(cursorEndsAt, 12L);

        service.getHistory(userId, cursor, 20);

        verify(entryRepository).findWeeklyHistory(userId, cursorEndsAt, 12L, 21);
    }

    @Test
    void returnsSecondPageFromDecodedCursor() {
        UUID userId = UUID.randomUUID();
        Instant cursorEndsAt = Instant.parse("2026-07-26T15:00:00Z");
        String cursor = cursorCodec.encode(cursorEndsAt, 12L);
        when(entryRepository.findWeeklyHistory(userId, cursorEndsAt, 12L, 3))
                .thenReturn(List.of(row(11L, Instant.parse("2026-07-19T15:00:00Z"), 7L)));

        RankingHistoryResponse response = service.getHistory(userId, cursor, 2);

        assertThat(response.content()).hasSize(1);
        assertThat(response.content().get(0).seasonCode()).isEqualTo("WEEKLY_11");
        assertThat(response.nextCursor()).isNull();
        assertThat(response.hasNext()).isFalse();
    }

    private RankingEntryRepository.RankingHistoryRow row(long seasonId, Instant endsAt, long finalRank) {
        return new RankingEntryRepository.RankingHistoryRow(
                seasonId,
                "WEEKLY_" + seasonId,
                endsAt.minusSeconds(604800),
                endsAt,
                finalRank,
                finalRank * 100
        );
    }
}
