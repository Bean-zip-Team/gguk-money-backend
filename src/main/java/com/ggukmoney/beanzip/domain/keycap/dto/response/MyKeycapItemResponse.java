package com.ggukmoney.beanzip.domain.keycap.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

@Schema(description = "내 보유 키캡 항목")
public record MyKeycapItemResponse(
        @Schema(description = "키캡 ID", example = "11111111-1111-1111-1111-111111111111")
        UUID keycapId,
        @Schema(description = "키캡 코드", example = "BASIC_BEAN")
        String code,
        @Schema(description = "키캡 이름", example = "기본 콩")
        String name,
        @Schema(description = "보유 조각 수", example = "3")
        int shardCount,
        @Schema(description = "보유 키캡 상태", example = "IN_PROGRESS")
        String status,
        @Schema(description = "현재 장착 여부", example = "false")
        boolean equipped
) {
}
