package com.ggukmoney.beanzip.domain.keycap.dto.response;

import java.time.Instant;
import java.util.UUID;

public record KeycapBoxHistoryItemResponse(
        UUID boxOpenId,
        String openMethod,
        UUID keycapId,
        int shardCount,
        boolean completed,
        Instant openedAt
) {
}
