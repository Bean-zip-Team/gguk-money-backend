package com.ggukmoney.beanzip.domain.friend.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "내 초대 코드예요.")
public record InviteCodeResponse(
        @Schema(description = "초대 코드예요. 영문 대문자와 숫자 8자이고 바뀌지 않아요. 공유 링크는 앱이 이 코드로 만들어요.", example = "K7P2QX9A")
        String code
) {
}
