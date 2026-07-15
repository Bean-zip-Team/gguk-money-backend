package com.ggukmoney.beanzip.domain.booster.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

@Schema(description = "부스터 활성화 요청")
public record BoosterActivateRequest(
        @Schema(description = "광고 시청 식별자", example = "11111111-1111-1111-1111-111111111111")
        @NotNull(message = "adViewId가 필요합니다.")
        UUID adViewId
) {
}
