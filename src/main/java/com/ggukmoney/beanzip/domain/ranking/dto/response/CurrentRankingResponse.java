package com.ggukmoney.beanzip.domain.ranking.dto.response;

import java.util.List;

public record CurrentRankingResponse(
        List<RankingItemResponse> items,
        MyRankingResponse myRank,
        long totalParticipantCount
) {
}
