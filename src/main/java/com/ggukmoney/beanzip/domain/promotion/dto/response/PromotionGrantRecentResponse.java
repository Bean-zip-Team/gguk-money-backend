package com.ggukmoney.beanzip.domain.promotion.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "최근 지급 내역")
public record PromotionGrantRecentResponse(
        @Schema(description = "지급 건 목록. 최신순.")
        List<PromotionGrantItemResponse> items
) {
}
