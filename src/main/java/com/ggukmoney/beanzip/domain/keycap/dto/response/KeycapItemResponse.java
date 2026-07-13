package com.ggukmoney.beanzip.domain.keycap.dto.response;

import java.util.UUID;

public record KeycapItemResponse(
        UUID keycapId,
        String code,
        String name,
        String grade,
        int requiredShardCount,
        int season,
        String imageUrl,
        String soundUrl
) {
}
