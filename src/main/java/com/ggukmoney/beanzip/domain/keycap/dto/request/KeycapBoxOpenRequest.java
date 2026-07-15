package com.ggukmoney.beanzip.domain.keycap.dto.request;

import com.ggukmoney.beanzip.domain.keycap.entity.KeycapBoxOpen;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "키캡 상자 개봉 요청")
public record KeycapBoxOpenRequest(
        @Schema(description = "개봉 방식", example = "FREE_TICKET")
        @NotNull(message = "openMethod가 필요합니다.")
        KeycapBoxOpen.OpenMethod openMethod,
        @Schema(description = "광고 보상 식별자. 광고 개봉 지원 시 사용합니다.", example = "ad-reward-001")
        String adRewardId
) {
}
