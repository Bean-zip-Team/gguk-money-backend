package com.ggukmoney.beanzip.domain.ranking.reward.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "내 주간 랭킹 보상")
public record MyWeeklyRankingRewardResponse(
        @Schema(description = "보상 ID")
        UUID rewardId,
        @Schema(description = "보상 순위", example = "1")
        int rewardRank,
        @Schema(description = "수령 포인트", example = "10000")
        long pointAmount,
        @Schema(description = "수령 상태", example = "CLAIMABLE")
        ClaimStatus claimStatus,
        @Schema(description = "수령 만료 시각")
        Instant expiresAt,
        @Schema(description = "수령 시각. 미수령이면 null")
        Instant claimedAt
) {

    public enum ClaimStatus {
        CLAIMED,
        EXPIRED,
        CONSENT_REQUIRED,
        CLAIMABLE
    }
}
