package com.ggukmoney.beanzip.domain.keycap.service;

import com.ggukmoney.beanzip.domain.keycap.dto.mapper.KeycapBoxMapper;
import com.ggukmoney.beanzip.domain.keycap.dto.response.KeycapBoxHistoryItemResponse;
import com.ggukmoney.beanzip.domain.keycap.dto.response.KeycapBoxHistoryResponse;
import com.ggukmoney.beanzip.domain.keycap.entity.KeycapBoxOpen;
import com.ggukmoney.beanzip.domain.keycap.repository.KeycapBoxOpenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class KeycapBoxHistoryService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private final KeycapBoxOpenRepository keycapBoxOpenRepository;
    private final KeycapBoxMapper keycapBoxMapper;
    private final KeycapBoxHistoryCursorCodec cursorCodec;

    @Transactional(readOnly = true)
    public KeycapBoxHistoryResponse getHistory(UUID userId, String cursor, Integer size) {
        int pageSize = validatePageSize(size);
        KeycapBoxHistoryCursorCodec.Cursor decodedCursor = cursorCodec.decode(cursor);

        List<KeycapBoxOpen> fetched = keycapBoxOpenRepository.findHistoryByUserId(
                userId,
                decodedCursor == null ? null : decodedCursor.openedAt(),
                decodedCursor == null ? null : decodedCursor.id(),
                PageRequest.of(0, pageSize + 1)
        );

        boolean hasNext = fetched.size() > pageSize;
        List<KeycapBoxOpen> page = hasNext ? fetched.subList(0, pageSize) : fetched;
        List<KeycapBoxHistoryItemResponse> content = page.stream()
                .map(keycapBoxMapper::mapToHistoryItemResponse)
                .toList();
        String nextCursor = hasNext ? encodeNextCursor(page.get(page.size() - 1)) : null;

        return new KeycapBoxHistoryResponse(content, nextCursor, hasNext);
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

    private String encodeNextCursor(KeycapBoxOpen boxOpen) {
        return cursorCodec.encode(boxOpen.getOpenedAt(), boxOpen.getId());
    }
}
