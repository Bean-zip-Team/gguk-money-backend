package com.ggukmoney.beanzip.domain.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Toss 연결 해제 웹훅 요청이에요.")
public record TossUnlinkWebhookRequest(
        @Schema(description = "Toss 사용자 키예요. 이 키로 연결된 회원을 찾아요.", example = "123456789")
        @NotBlank String userKey,
        @Schema(description = "Toss가 보내는 연결 해제 이벤트 종류예요. `UNLINK`는 연결 끊기, `WITHDRAWAL_TERMS`는 약관 동의 철회, `WITHDRAWAL_TOSS`는 Toss 회원 탈퇴예요. 세 값 모두 회원 탈퇴로 처리하고, 다른 값이면 400을 돌려줘요.", example = "UNLINK")
        @NotBlank String referrer
) {
}
