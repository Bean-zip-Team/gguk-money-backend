package com.ggukmoney.beanzip.domain.booster.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "부스터 활성화 요청이에요.")
public record BoosterActivateRequest(
        @Schema(description = "Toss IntegratedAd에서 광고를 불러오고 보여줄 때 쓴 광고 그룹 ID예요. 비어 있으면 안 돼요.", example = "ait.dev.43daa14da3ae487b")
        @NotBlank(message = "adGroupId가 필요합니다.")
        String adGroupId
) {
}
