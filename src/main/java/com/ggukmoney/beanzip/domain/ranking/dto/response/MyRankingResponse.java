package com.ggukmoney.beanzip.domain.ranking.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "내 랭킹 정보")
public record MyRankingResponse(
        @Schema(description = "내 순위. 랭킹 참가자가 아니면 null", example = "2")
        Long rank,
        @Schema(description = "내 점수. 랭킹 참가자가 아니면 0", example = "950")
        long score,
        @Schema(description = "1위와의 점수 차이", example = "250")
        long scoreGapToFirst
) {
}
