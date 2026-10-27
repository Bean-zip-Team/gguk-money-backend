package com.ggukmoney.beanzip.domain.ranking.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;
import com.ggukmoney.beanzip.domain.ranking.reward.dto.MyWeeklyRankingRewardResponse.RewardStatus;

@Schema(description = "주간 랭킹 히스토리 항목이에요.")
public record RankingHistoryItemResponse(
        @Schema(description = "주간 시즌 코드예요.", example = "WEEKLY_20260720")
        String seasonCode,
        @Schema(description = "시즌 시작 시각이에요.", example = "2026-07-19T15:00:00Z")
        Instant startedAt,
        @Schema(description = "시즌 종료 시각이에요.", example = "2026-07-26T15:00:00Z")
        Instant endsAt,
        @Schema(description = "해당 주간 시즌에서 확정된 내 순위예요.", example = "7")
        Long myFinalRank,
        @Schema(description = "해당 주간 시즌에서 확정된 내 랭킹 점수예요.", example = "950")
        long myFinalScore,
        @Schema(description = "이 시즌의 내 주간 랭킹 보상 ID예요. 보상 대상이 아니었으면 null이에요.", example = "22222222-2222-2222-2222-222222222222")
        UUID rewardId,
        @Schema(description = "이 시즌의 내 보상 상태예요. `NONE`은 보상 대상이 아님, `PENDING`은 수령 대기, `CLAIMED`는 수령 완료, `EXPIRED`는 수령 기간 만료예요.", example = "CLAIMED")
        RewardStatus rewardStatus,
        @Schema(description = "보상 포인트예요. 보상 대상이 아니었으면 null이에요.", example = "5000")
        Long rewardPointAmount,
        @Schema(description = "보상 수령 만료 시각이에요. 보상 대상이 아니었으면 null이에요.", example = "2026-07-29T15:00:00Z")
        Instant expiresAt,
        @Schema(description = "보상을 수령한 시각이에요. 아직 수령하지 않았거나 보상 대상이 아니었으면 null이에요.", example = "2026-07-27T03:00:00Z")
        Instant claimedAt
) {
}
