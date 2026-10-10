package com.ggukmoney.beanzip.domain.ranking.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "내 랭킹 정보예요.")
public record MyRankingResponse(
        @Schema(description = "내 현재 순위예요. 랭킹 참가자가 아니면 null이에요.", example = "7")
        Long rank,
        @Schema(description = "직전 주 최종 순위예요. 직전 주에 참가하지 않았으면 null이에요.", example = "10")
        Long previousRank,
        @Schema(description = "순위 변화예요. `previousRank` 값에서 `rank` 값을 뺀 값이에요. 둘 중 하나라도 null이면 null이에요.", example = "3")
        Long rankChange,
        @Schema(description = "내 현재 점수예요. 랭킹 참가자가 아니면 0이에요.", example = "950")
        long score,
        @Schema(description = "1위와의 점수 차이예요. 내가 1위면 0이에요.", example = "250")
        long scoreGapToFirst,
        @Schema(description = "예상 보상 순위예요. 보상권 밖이면 null이에요.", example = "2")
        Integer rewardRank,
        @Schema(description = "예상 보상 포인트예요. 보상권 밖이면 null이에요.", example = "5000")
        Long rewardPointAmount,
        @Schema(description = "보상권 진입에 필요한 추가 점수예요. 보상 대상에서 제외됐거나 보상 정책을 쓰지 않으면 null이에요.", example = "25")
        Long scoreGapToReward,
        @Schema(description = "현재 순위 변동 알림을 보낼 수 있는지 여부예요. `RANK_CHANGE` 알림에 동의하고 켜 둔 경우에만 true예요.", example = "true")
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
