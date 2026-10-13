package com.ggukmoney.beanzip.domain.promotion.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "최근 지급 내역이에요.")
public record PromotionGrantRecentResponse(
        @Schema(description = "지급 건 목록이에요. 발급 시각 기준 최신순이에요.")
        List<PromotionGrantItemResponse> items
) {
}
