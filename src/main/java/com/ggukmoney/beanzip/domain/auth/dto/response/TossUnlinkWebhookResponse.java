package com.ggukmoney.beanzip.domain.auth.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Toss 연결 해제 웹훅 응답")
public record TossUnlinkWebhookResponse(
        @Schema(description = "처리 여부", example = "true")
        boolean processed,
        @Schema(description = "Toss referrer", example = "beanzip")
        String referrer
) {
}
