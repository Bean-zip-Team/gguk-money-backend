package com.ggukmoney.beanzip.domain.ranking.dto.response;

public record MyRankingResponse(
        Long rank,
        long score,
        long scoreGapToFirst
) {
}
