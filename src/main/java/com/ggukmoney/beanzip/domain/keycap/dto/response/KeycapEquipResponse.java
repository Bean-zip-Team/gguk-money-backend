package com.ggukmoney.beanzip.domain.keycap.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

@Schema(description = "키캡 장착 응답이에요.")
public record KeycapEquipResponse(
        @Schema(description = "장착한 키캡 ID예요.", example = "11111111-1111-1111-1111-111111111111")
        UUID keycapId,
        @Schema(description = "장착 여부예요. 장착에 성공하면 항상 true예요.", example = "true")
        boolean equipped
) {
}
