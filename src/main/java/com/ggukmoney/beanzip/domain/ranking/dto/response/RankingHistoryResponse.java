package com.ggukmoney.beanzip.domain.ranking.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "주간 랭킹 히스토리 페이지 응답이에요.")
public record RankingHistoryResponse(
        @Schema(description = "종료된 주간 랭킹 이력 목록이에요. 최신 시즌부터 정렬돼요.")
        List<RankingHistoryItemResponse> content,
        @Schema(description = "다음 페이지 조회에 전달할 커서예요. 다음 페이지가 없으면 null이에요.", example = "MjAyNi0wNy0yNlQxNTowMDowMFp8MTIz")
        String nextCursor,
        @Schema(description = "다음 페이지 존재 여부예요.", example = "true")
        boolean hasNext,
        @Schema(description = "실제로 수령한 주간 랭킹 보상 포인트 합계예요. 현재 페이지가 아니라 모든 시즌을 합산해요.", example = "17500")
        long claimedRewardPointTotal
) {
}
