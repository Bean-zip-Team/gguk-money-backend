package com.ggukmoney.beanzip.domain.ranking.service;

import com.ggukmoney.beanzip.domain.ranking.dto.response.RankingHistoryItemResponse;
import com.ggukmoney.beanzip.domain.ranking.dto.response.RankingHistoryResponse;
import com.ggukmoney.beanzip.domain.ranking.repository.RankingEntryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RankingHistoryService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private final RankingEntryRepository entryRepository;
    private final RankingHistoryCursorCodec cursorCodec;

    @Transactional(readOnly = true)
    public RankingHistoryResponse getHistory(UUID userId, String cursor, Integer size) {
        int pageSize = validatePageSize(size);
        RankingHistoryCursorCodec.Cursor decodedCursor = cursorCodec.decode(cursor);
        List<RankingEntryRepository.RankingHistoryRow> fetched = entryRepository.findWeeklyHistory(
                userId,
                decodedCursor == null ? null : decodedCursor.endsAt(),
                decodedCursor == null ? null : decodedCursor.seasonId(),
                pageSize + 1
        );

        boolean hasNext = fetched.size() > pageSize;
        List<RankingEntryRepository.RankingHistoryRow> page = hasNext ? fetched.subList(0, pageSize) : fetched;
        List<RankingHistoryItemResponse> content = page.stream()
                .map(this::toResponse)
                .toList();
        String nextCursor = hasNext ? encodeNextCursor(page.get(page.size() - 1)) : null;

        return new RankingHistoryResponse(content, nextCursor, hasNext);
    }

    private int validatePageSize(Integer size) {
        if (size == null) {
            return DEFAULT_PAGE_SIZE;
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "COMMON_VALIDATION_ERROR");
        }
        return size;
    }

    private RankingHistoryItemResponse toResponse(RankingEntryRepository.RankingHistoryRow row) {
        return new RankingHistoryItemResponse(
                row.seasonCode(),
                row.startedAt(),
                row.endsAt(),
                row.finalRank(),
                row.finalScore()
        );
    }

    private String encodeNextCursor(RankingEntryRepository.RankingHistoryRow row) {
        return cursorCodec.encode(row.endsAt(), row.seasonId());
    }
}
