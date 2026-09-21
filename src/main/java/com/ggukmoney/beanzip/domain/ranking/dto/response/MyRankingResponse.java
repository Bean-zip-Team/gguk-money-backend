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
        long scoreGapToFirst,
        @Schema(description = "예상 보상 순위. 보상권 밖이면 null", example = "2")
        Integer rewardRank,
        @Schema(description = "예상 보상 포인트. 보상권 밖이면 null", example = "5000")
        Long rewardPointAmount,
        @Schema(description = "보상권 진입에 필요한 추가 점수. 보상 제외 또는 정책 미사용 시 null", example = "25")
        Long scoreGapToReward,
        @Schema(description = "현재 순위 변동 알림 전송 동의 여부", example = "true")
        boolean rankChangeNotificationEnabled
) {

    public MyRankingResponse(
            Long rank,
            Long previousRank,
            Long rankChange,
            long score,
            long scoreGapToFirst
    ) {
        this(rank, previousRank, rankChange, score, scoreGapToFirst, null, null, null, false);
    }
}
