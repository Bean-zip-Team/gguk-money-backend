package com.ggukmoney.beanzip.domain.keycap.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

@Schema(description = "키캡 장착 응답")
public record KeycapEquipResponse(
        @Schema(description = "장착한 키캡 ID", example = "11111111-1111-1111-1111-111111111111")
        UUID keycapId,
        @Schema(description = "장착 여부", example = "true")
        boolean equipped
) {
}
