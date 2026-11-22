package com.ggukmoney.beanzip.domain.friend.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

@Schema(description = "친구 한 명을 가리키는 값이에요.")
public record FriendProfileResponse(
        @Schema(description = "사용자 ID예요.", example = "33333333-3333-3333-3333-333333333333")
        UUID userId,
        @Schema(description = "이름(실명)이에요. 친구 사이에서만 가리지 않고 보여 줘요. 토스에서 이름을 받지 못한 사용자는 `null`일 수 있어요.", example = "박준호", nullable = true)
        String name
) {
}
