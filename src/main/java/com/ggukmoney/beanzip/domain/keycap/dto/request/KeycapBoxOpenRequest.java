package com.ggukmoney.beanzip.domain.keycap.dto.request;

import com.ggukmoney.beanzip.domain.keycap.entity.KeycapBoxOpen;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "키캡 상자 개봉 요청")
public record KeycapBoxOpenRequest(
        @Schema(description = "개봉 방식", example = "ADVERTISEMENT")
        @NotNull(message = "openMethod가 필요합니다.")
        KeycapBoxOpen.OpenMethod openMethod,
        @Schema(description = "선택 값. 광고 Provider 검증이 도입된 뒤 Provider가 발급한 보상 또는 트랜잭션 식별자가 있을 때만 사용합니다.")
        String adRewardId
) {
}
