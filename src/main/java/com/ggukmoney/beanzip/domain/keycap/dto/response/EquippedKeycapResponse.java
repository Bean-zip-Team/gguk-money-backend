package com.ggukmoney.beanzip.domain.keycap.dto.response;

import java.util.UUID;

public record EquippedKeycapResponse(
        UUID keycapId,
        String code,
        String name,
        String imageUrl
) {
}
