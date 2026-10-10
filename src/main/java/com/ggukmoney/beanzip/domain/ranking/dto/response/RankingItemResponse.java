package com.ggukmoney.beanzip.domain.ranking.dto.response;

import com.ggukmoney.beanzip.domain.keycap.dto.response.EquippedKeycapResponse;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

@Schema(description = "랭킹 목록 항목이에요.")
public record RankingItemResponse(
        @Schema(description = "현재 순위예요. 1부터 시작해요.", example = "1")
        long rank,
        @Schema(description = "직전 주 최종 순위예요. 직전 주에 참가하지 않았으면 null이에요.", example = "3")
        Long previousRank,
        @Schema(description = "순위 변화예요. `previousRank` 값에서 `rank` 값을 뺀 값이에요. `previousRank` 값이 null이면 null이에요.", example = "2")
        Long rankChange,
        @Schema(description = "사용자 ID예요.", example = "11111111-1111-1111-1111-111111111111")
        UUID userId,
        @Schema(description = "사용자 닉네임이에요.", example = "Bean")
        String nickname,
        @Schema(description = "사용자 프로필 이미지 URL이에요.", example = "https://example.com/profile.png")
        String profileImageUrl,
        @Schema(description = "랭킹 점수예요.", example = "1200")
        long score,
        @Schema(description = "현재 로그인한 사용자인지 여부예요.", example = "false")
        boolean isMe,
        @Schema(description = "예상 보상 순위예요. 보상권 밖이면 null이에요.", example = "1")
        Integer rewardRank,
        @Schema(description = "예상 보상 포인트예요. 보상권 밖이면 null이에요.", example = "10000")
        Long rewardPointAmount,
        @Schema(description = "장착한 키캡이에요. 프로필 사진 자리에 써요(BEA-316). 장착한 키캡이 없으면 온보딩 기본 키캡이에요.")
        EquippedKeycapResponse equippedKeycap
) {

    public RankingItemResponse(
            long rank,
            Long previousRank,
            Long rankChange,
            UUID userId,
            String nickname,
            String profileImageUrl,
            long score,
            boolean isMe
    ) {
        this(rank, previousRank, rankChange, userId, nickname, profileImageUrl, score, isMe, null, null, null);
    }
}
