package com.ggukmoney.beanzip.domain.keycap.dto.request;

import com.ggukmoney.beanzip.domain.keycap.entity.KeycapBoxOpen;
import jakarta.validation.constraints.NotNull;

public record KeycapBoxOpenRequest(
        @NotNull(message = "openMethod가 필요합니다.")
        KeycapBoxOpen.OpenMethod openMethod,
        String adRewardId
) {
}
