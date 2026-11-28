package com.ggukmoney.beanzip.domain.friend.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

@Schema(description = "응원 보내기 요청이에요.")
public record CheerSendRequest(
        @Schema(description = "응원할 친구의 사용자 ID예요.", example = "33333333-3333-3333-3333-333333333333")
        @NotNull UUID toUserId
) {
}
