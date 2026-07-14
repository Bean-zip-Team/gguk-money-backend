package com.ggukmoney.beanzip.domain.keycap.dto.response;

import java.util.UUID;

public record KeycapEquipResponse(
        UUID keycapId,
        boolean equipped
) {
}
