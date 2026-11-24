package com.ggukmoney.beanzip.domain.friend.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "초대 수락 요청이에요.")
public record InviteAcceptRequest(
        @Schema(description = "초대 링크에 들어 있던 초대 코드예요.", example = "K7P2QX9A")
        @NotBlank @Size(max = 64) String inviteCode
) {
}
