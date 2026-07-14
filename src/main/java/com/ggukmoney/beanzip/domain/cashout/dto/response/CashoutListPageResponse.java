package com.ggukmoney.beanzip.domain.cashout.dto.response;

import java.util.List;

public record CashoutListPageResponse(
        List<CashoutListItemResponse> items,
        String nextCursor,
        boolean hasMore
) {
}
