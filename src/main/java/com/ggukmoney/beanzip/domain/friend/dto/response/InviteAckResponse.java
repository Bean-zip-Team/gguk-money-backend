package com.ggukmoney.beanzip.domain.friend.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "초대 성공 확인 결과예요.")
public record InviteAckResponse(
        @Schema(description = "이번 확인으로 받은 조각 수예요. 이미 확인한 건은 세지 않아요. 정지된 사용자는 0이에요.", example = "10")
        int shardsGranted,
        @Schema(description = "확인 후 내 조각 잔액이에요.", example = "135")
        int shardBalance
) {
}
