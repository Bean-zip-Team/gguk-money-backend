package com.ggukmoney.beanzip.domain.friend.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

@Schema(description = "응원을 보낸 결과예요.")
public record CheerSendResponse(
        @Schema(description = "보낸 응원의 ID예요.", example = "9b2e6c1a-4d7f-4b0e-8a35-2c9d1e7f6b40")
        UUID cheerId,
        @Schema(description = "오늘 처음 보낸 응원인지예요. `true`면 보낸 사람도 조각을 받아요.", example = "true")
        boolean firstOfDay,
        @Schema(description = "이 응원으로 보낸 사람이 받은 조각 수예요. 오늘 첫 응원일 때만 0보다 커요.", example = "1")
        int shardsGranted,
        @Schema(description = "오늘 더 응원할 수 있는 친구 수예요.", example = "6")
        int cheersLeft
) {
}
