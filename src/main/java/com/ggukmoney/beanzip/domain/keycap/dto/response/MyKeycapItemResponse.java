package com.ggukmoney.beanzip.domain.keycap.dto.response;

import java.util.UUID;

public record MyKeycapItemResponse(
        UUID keycapId,
        String code,
        String name,
        int shardCount,
        String status,
        boolean equipped
) {
}
