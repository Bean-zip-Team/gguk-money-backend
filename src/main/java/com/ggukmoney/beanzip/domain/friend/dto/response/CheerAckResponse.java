package com.ggukmoney.beanzip.domain.friend.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "응원 확인 결과예요.")
public record CheerAckResponse(
        @Schema(description = "이번 확인으로 받은 조각 수예요. 이미 확인한 건은 세지 않아요. 정지된 사용자는 0이에요.", example = "3")
        int shardsGranted,
        @Schema(description = "하루 상한 때문에 조각을 받지 못한 응원이 있었는지예요. `true`면 앱은 '오늘 받을 수 있는 조각은 다 받았어요'를 보여 줘요.", example = "false")
        boolean capped,
        @Schema(description = "확인 후 내 조각 잔액이에요.", example = "138")
        int shardBalance
) {
}
