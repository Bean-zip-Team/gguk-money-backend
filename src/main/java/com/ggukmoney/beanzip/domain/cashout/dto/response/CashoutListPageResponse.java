package com.ggukmoney.beanzip.domain.cashout.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "출금 목록 페이지 응답")
public record CashoutListPageResponse(
        @Schema(description = "출금 목록")
        List<CashoutListItemResponse> items,
        @Schema(description = "다음 페이지 커서", example = "MTIz")
        String nextCursor,
        @Schema(description = "다음 페이지 존재 여부", example = "true")
        boolean hasMore
) {
}
