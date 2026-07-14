package com.ggukmoney.beanzip.domain.point.dto.response;

import java.util.List;

public record PointLedgerPageResponse(
        List<PointLedgerItemResponse> items,
        String nextCursor,
        boolean hasMore
) {
}
