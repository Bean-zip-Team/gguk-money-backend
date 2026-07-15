package com.ggukmoney.beanzip.domain.point.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "포인트 원장 페이지 응답")
public record PointLedgerPageResponse(
        @Schema(description = "원장 항목 목록")
        List<PointLedgerItemResponse> items,
        @Schema(description = "다음 페이지 커서", example = "MTIz")
        String nextCursor,
        @Schema(description = "다음 페이지 존재 여부", example = "true")
        boolean hasMore
) {
}
