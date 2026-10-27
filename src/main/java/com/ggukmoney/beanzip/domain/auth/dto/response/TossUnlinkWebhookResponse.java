package com.ggukmoney.beanzip.domain.auth.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Toss 연결 해제 웹훅 응답이에요.")
public record TossUnlinkWebhookResponse(
        @Schema(description = "웹훅 처리 여부예요. 연결된 회원이 없거나 이미 탈퇴한 회원이어도 `true`예요.", example = "true")
        boolean processed,
        @Schema(description = "요청으로 받은 연결 해제 이벤트 종류를 그대로 돌려줘요.", example = "UNLINK")
        String referrer
) {
}
