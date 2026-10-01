package com.ggukmoney.beanzip.domain.ranking.reward.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "내 주간 랭킹 보상")
public record MyWeeklyRankingRewardResponse(
        @Schema(description = "보상 ID")
        UUID rewardId,
        @Schema(description = "보상 순위", example = "1", nullable = true)
        Integer rewardRank,
        @Schema(description = "수령 포인트", example = "10000", nullable = true)
        Long pointAmount,
        @Schema(description = "보상 상태", example = "PENDING")
        RewardStatus claimStatus,
        @Schema(description = "수령 만료 시각")
        Instant expiresAt,
        @Schema(description = "수령 시각. 미수령이면 null")
        Instant claimedAt
) {

    public enum RewardStatus {
        NONE,
        PENDING,
        CLAIMED,
        EXPIRED
    }

    public static MyWeeklyRankingRewardResponse none() {
        return new MyWeeklyRankingRewardResponse(null, null, null, RewardStatus.NONE, null, null);
    }
}
