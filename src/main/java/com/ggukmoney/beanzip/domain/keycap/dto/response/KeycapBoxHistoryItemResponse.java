package com.ggukmoney.beanzip.domain.keycap.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "키캡 상자 개봉 이력 항목이에요.")
public record KeycapBoxHistoryItemResponse(
        @Schema(description = "상자 개봉 ID예요.", example = "11111111-1111-1111-1111-111111111111")
        UUID boxOpenId,
        @Schema(description = "개봉 방식이에요. `FREE`는 무료 개봉, `ADVERTISEMENT`는 광고 개봉이에요. `BULK_REWARD`는 알림 동의 보상으로 쌓인 상자를 한 번에 연 기록이에요(BEA-280).", example = "FREE")
        String openMethod,
        @Schema(description = "지급된 키캡 ID예요.", example = "22222222-2222-2222-2222-222222222222")
        UUID keycapId,
        @Schema(description = "지급된 조각 개수예요.", example = "1")
        int shardCount,
        @Schema(description = "이번 개봉으로 키캡이 완성됐는지 여부예요.", example = "false")
        boolean completed,
        @Schema(description = "개봉 시각이에요.", example = "2026-07-15T01:00:00Z")
        Instant openedAt
) {
}
