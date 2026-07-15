package com.ggukmoney.beanzip.domain.keycap.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

@Schema(description = "장착 키캡 정보")
public record EquippedKeycapResponse(
        @Schema(description = "키캡 ID", example = "11111111-1111-1111-1111-111111111111")
        UUID keycapId,
        @Schema(description = "키캡 코드", example = "BASIC_BEAN")
        String code,
        @Schema(description = "키캡 이름", example = "기본 콩")
        String name,
        @Schema(description = "키캡 이미지 URL", example = "https://example.com/keycap.png")
        String imageUrl
) {
}
