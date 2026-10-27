package com.ggukmoney.beanzip.domain.ranking.reward.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Schema(description = "가장 최근 종료된 주간 랭킹 보상 결과예요.")
public record LatestWeeklyRankingRewardResponse(
        @Schema(description = "가장 최근 종료된 주간 랭킹 시즌이에요.")
        Season season,
        @Schema(description = "보상 수상자 목록이에요. 보상 순위 오름차순으로 정렬돼요. 보상권 순위에 들었어도 보상 확정 시점에 `RANK_CHANGE` 알림을 받을 수 없던 사용자는 빠져요.")
        List<Winner> winners,
        @Schema(description = "내 보상이에요. 내가 수상자가 아니면 상태가 `NONE`이고 나머지 필드는 null이에요.")
        MyWeeklyRankingRewardResponse myReward,
        @Schema(description = "내 수상 결과를 아직 열람하지 않았는지 여부예요. 내 보상이 있고 열람 기록이 없을 때만 true예요.", example = "true")
        boolean hasUnreadWinner
) {

    @Schema(description = "종료된 주간 랭킹 시즌이에요.")
    public record Season(
            @Schema(description = "주간 시즌 코드예요.", example = "WEEKLY_20260720")
            String seasonCode,
            @Schema(description = "시즌 시작 시각이에요.", example = "2026-07-19T15:00:00Z")
            Instant startedAt,
            @Schema(description = "시즌 종료 시각이에요.", example = "2026-07-26T15:00:00Z")
            Instant endedAt
    ) {
    }

    @Schema(description = "주간 랭킹 보상 수상자예요.")
    public record Winner(
            @Schema(description = "보상 순위예요.", example = "1")
            int rewardRank,
            @Schema(description = "시즌 종료 때 확정된 최종 순위예요.", example = "1")
            long sourceFinalRank,
            @Schema(description = "사용자 ID예요.", example = "11111111-1111-1111-1111-111111111111")
            UUID userId,
            @Schema(description = "가운데 글자를 `*` 문자로 가린 닉네임이에요. 두 글자면 마지막 글자를 가리고, 한 글자면 그대로 보여요. 닉네임이 비어 있으면 null이에요.", example = "B**n")
            String nickname,
            @Schema(description = "사용자 프로필 이미지 URL이에요.", example = "https://example.com/profile.png")
            String profileImageUrl,
            @Schema(description = "시즌에서 확정된 최종 랭킹 점수예요.", example = "1200")
            long finalScore,
            @Schema(description = "보상 포인트예요.", example = "10000")
            long pointAmount,
            @Schema(description = "현재 로그인한 사용자인지 여부예요.", example = "false")
            boolean isMe
    ) {
    }
}
