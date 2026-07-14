package com.ggukmoney.beanzip.domain.keycap.dto.response;

import java.util.List;

public record KeycapBoxHistoryResponse(
        List<KeycapBoxHistoryItemResponse> content,
        String nextCursor,
        boolean hasNext
) {
}
