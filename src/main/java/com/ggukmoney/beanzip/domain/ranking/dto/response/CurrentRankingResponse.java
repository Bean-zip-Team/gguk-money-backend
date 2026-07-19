package com.ggukmoney.beanzip.domain.ranking.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "현재 전체 랭킹 응답")
public record CurrentRankingResponse(
        @Schema(description = "상위 랭킹 목록")
        List<RankingItemResponse> items,
        @Schema(description = "내 랭킹 정보")
        MyRankingResponse myRank,
        @Schema(description = "전체 랭킹 참가자 수", example = "124")
        long totalParticipantCount
) {
}
