package com.ggukmoney.beanzip.domain.keycap.dto.response;

import java.util.List;

public record MyKeycapListResponse(
        List<MyKeycapItemResponse> keycaps
) {
}
