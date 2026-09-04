package com.ggukmoney.beanzip.domain.ranking.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "내 랭킹 정보")
public record MyRankingResponse(
        @Schema(description = "내 현재 순위. 랭킹 참가자가 아니면 null", example = "7")
        Long rank,
        @Schema(description = "직전 주 최종 순위. 미참가 시 null", example = "10")
        Long previousRank,
        @Schema(description = "순위 변화. previousRank - rank", example = "3")
        Long rankChange,
        @Schema(description = "내 현재 점수. 랭킹 참가자가 아니면 0", example = "950")
        long score,
        @Schema(description = "1위와 점수 차이", example = "250")
        long scoreGapToFirst
) {
}
