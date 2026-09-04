package com.ggukmoney.beanzip.domain.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Toss 연결 해제 웹훅 요청")
public record TossUnlinkWebhookRequest(
        @Schema(description = "Toss 사용자 키", example = "123456789")
        @NotBlank String userKey,
        @Schema(description = "Toss referrer", example = "beanzip")
        @NotBlank String referrer
) {
}
