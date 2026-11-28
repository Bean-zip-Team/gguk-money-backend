package com.ggukmoney.beanzip.domain.friend.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.UUID;

@Schema(description = "초대 링크 방문 기록 요청이에요.")
public record InviteVisitRequest(
        @Schema(description = "초대 링크에 들어 있던 초대 코드예요. 1~64자이고 영문, 숫자, `_`, `-` 만 받아요.", example = "K7P2QX9A")
        @NotBlank @Size(max = 64) @Pattern(regexp = "[A-Za-z0-9_-]+") String inviteCode,
        @Schema(description = "앱이 이 기기에서 처음 한 번 만든 UUID 예요. 초대 코드와 따로, 지우지 않고 보관해요. 같은 링크를 다시 눌러도 한 번만 세는 데 써요.", example = "3f6c2a9e-8d1b-4c7e-9a52-1b0d6e4f7a21")
        @NotNull UUID visitKey
) {
}
