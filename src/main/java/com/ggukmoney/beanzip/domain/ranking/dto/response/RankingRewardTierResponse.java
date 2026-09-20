package com.ggukmoney.beanzip.domain.ranking.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "주간 랭킹 보상 등수와 포인트")
public record RankingRewardTierResponse(
        @Schema(description = "보상 순위", example = "1")
        int rewardRank,
        @Schema(description = "보상 포인트", example = "10000")
        long pointAmount
) {
}
