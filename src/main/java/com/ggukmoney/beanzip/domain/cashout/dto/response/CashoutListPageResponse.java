package com.ggukmoney.beanzip.domain.cashout.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "출금 목록 페이지 응답이에요.")
public record CashoutListPageResponse(
        @Schema(description = "출금 목록이에요. 최신 신청부터 정렬해요.")
        List<CashoutListItemResponse> items,
        @Schema(description = "다음 페이지 커서예요. 다음 페이지가 없으면 null이에요.", example = "MTIz")
        String nextCursor,
        @Schema(description = "다음 페이지가 있는지 여부예요.", example = "true")
        boolean hasMore
) {
}
