package com.ggukmoney.beanzip.domain.ranking.reward.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "내 주간 랭킹 보상이에요.")
public record MyWeeklyRankingRewardResponse(
        @Schema(description = "보상 ID예요. 보상 수령과 열람 기록 API의 경로에 써요. 보상이 없으면 null이에요.", example = "22222222-2222-2222-2222-222222222222")
        UUID rewardId,
        @Schema(description = "보상 순위예요. 보상이 없으면 null이에요.", example = "1", nullable = true)
        Integer rewardRank,
        @Schema(description = "수령할 포인트예요. 보상이 없으면 null이에요.", example = "10000", nullable = true)
        Long pointAmount,
        @Schema(description = "보상 상태예요. `NONE`은 보상 대상이 아님, `PENDING`은 수령 대기, `CLAIMED`는 수령 완료, `EXPIRED`는 수령 기간 만료예요.", example = "PENDING")
        RewardStatus claimStatus,
        @Schema(description = "수령 만료 시각이에요. 보상을 확정한 시각부터 3일 뒤예요. 보상이 없으면 null이에요.", example = "2026-07-29T15:00:00Z")
        Instant expiresAt,
        @Schema(description = "수령 시각이에요. 아직 수령하지 않았으면 null이에요.", example = "2026-07-27T03:00:00Z")
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
