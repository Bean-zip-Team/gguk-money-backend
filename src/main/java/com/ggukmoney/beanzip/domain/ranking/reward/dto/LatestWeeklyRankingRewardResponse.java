package com.ggukmoney.beanzip.domain.ranking.reward.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Schema(description = "가장 최근 종료된 주간 랭킹 보상 결과")
public record LatestWeeklyRankingRewardResponse(
        Season season,
        List<Winner> winners,
        MyWeeklyRankingRewardResponse myReward
) {

    @Schema(description = "종료된 주간 랭킹 시즌")
    public record Season(
            String seasonCode,
            Instant startedAt,
            Instant endedAt
    ) {
    }

    @Schema(description = "주간 랭킹 보상 수상자")
    public record Winner(
            int rewardRank,
            long sourceFinalRank,
            UUID userId,
            String nickname,
            String profileImageUrl,
            long finalScore,
            long pointAmount,
            boolean isMe
    ) {
    }
}
