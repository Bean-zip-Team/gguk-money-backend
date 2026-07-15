package com.ggukmoney.beanzip.domain.booster.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "부스터 활성화 요청")
public record BoosterActivateRequest(
        @Schema(description = "Toss IntegratedAd에서 광고 로드/표시에 사용한 광고 그룹 ID", example = "ait.dev.43daa14da3ae487b")
        @NotBlank(message = "adGroupId가 필요합니다.")
        String adGroupId
) {
}
