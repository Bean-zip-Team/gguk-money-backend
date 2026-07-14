package com.ggukmoney.beanzip.domain.keycap.dto.response;

import java.time.Instant;
import java.util.UUID;

public record KeycapBoxOpenResponse(
        UUID boxOpenId,
        UUID keycapId,
        int shardCount,
        boolean completed,
        Instant openedAt
) {
}
