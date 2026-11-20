package com.ggukmoney.beanzip.domain.friend.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

@Schema(description = "초대 링크로 새로 가입해서 초대가 성공한 결과예요.")
public record InviteSignupResponse(
        @Schema(description = "나를 초대한 사람의 사용자 ID예요.", example = "11111111-1111-1111-1111-111111111111")
        UUID inviterUserId,
        @Schema(description = "나를 초대한 사람의 이름이에요. 토스에서 이름을 받지 못한 사용자는 `null`일 수 있어요.", example = "김민지", nullable = true)
        String inviterName,
        @Schema(description = "가입하면서 받은 조각 수예요.", example = "10")
        int shardsGranted
) {
}
