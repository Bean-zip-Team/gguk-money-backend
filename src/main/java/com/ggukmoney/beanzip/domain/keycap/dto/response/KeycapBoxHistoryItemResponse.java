package com.ggukmoney.beanzip.domain.keycap.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "키캡 상자 개봉 이력 항목")
public record KeycapBoxHistoryItemResponse(
        @Schema(description = "상자 개봉 ID", example = "11111111-1111-1111-1111-111111111111")
        UUID boxOpenId,
        @Schema(description = "개봉 방식", example = "FREE_TICKET")
        String openMethod,
        @Schema(description = "지급된 키캡 ID", example = "22222222-2222-2222-2222-222222222222")
        UUID keycapId,
        @Schema(description = "지급 조각 수", example = "1")
        int shardCount,
        @Schema(description = "이번 개봉으로 키캡이 완성되었는지 여부", example = "false")
        boolean completed,
        @Schema(description = "개봉 시각", example = "2026-07-15T01:00:00Z")
        Instant openedAt
) {
}
