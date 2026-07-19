package com.ggukmoney.beanzip.domain.ranking.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

@Schema(description = "랭킹 목록 항목")
public record RankingItemResponse(
        @Schema(description = "순위. 1부터 시작", example = "1")
        long rank,
        @Schema(description = "사용자 ID", example = "11111111-1111-1111-1111-111111111111")
        UUID userId,
        @Schema(description = "사용자 닉네임", example = "Bean")
        String nickname,
        @Schema(description = "사용자 프로필 이미지 URL", example = "https://example.com/profile.png")
        String profileImageUrl,
        @Schema(description = "랭킹 점수. 누적 유효 탭 수", example = "1200")
        long score,
        @Schema(description = "현재 로그인한 사용자인지 여부", example = "false")
        boolean isMe
) {
}
