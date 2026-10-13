package com.ggukmoney.beanzip.domain.keycap.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/**
 * 조각 지갑 상태 (BEA-329). 상자 시절 필드는 구버전 앱이 깨지지 않도록 중립값으로 남긴다.
 */
@Schema(description = "키캡 조각 상태 응답이에요.")
public record KeycapBoxStatusResponse(
        @Schema(description = "현재 보유한 조각 개수예요.", example = "12")
        int shardBalance,
        @Schema(description = "뽑기 1회에 드는 조각 개수예요. `keycap.draw.price` 설정으로 정해요. 기본값은 5예요.", example = "5")
        int drawPrice,
        @Schema(description = "조각 잔액이 뽑기 가격 이상인지 여부예요.", example = "true")
        boolean canDraw,
        @Schema(description = "현재 탭 세션에서 쌓인 조각 진행 탭 수예요. 세션이 없거나 유휴 시간이 지나 만료됐으면 0이에요.", example = "45")
        long shardProgressTapCount,
        @Schema(description = "다음 조각을 얻는 데 필요한 탭 수예요. 세션 누적 기준이라서 `shardProgressTapCount` 필드가 이 값에 닿으면 조각이 1개 생겨요.", example = "100")
        int nextShardRequiredTapCount,
        @Schema(description = "[deprecated] 상자는 사라졌어요(BEA-329). 항상 0이에요. 조각 수는 `shardBalance` 필드를 보세요.", example = "0", deprecated = true)
        int boxBalance,
        @Schema(description = "[deprecated] 상자는 사라졌어요. 항상 false예요.", example = "false", deprecated = true)
        boolean canFreeOpen,
        @Schema(description = "[deprecated] 상자는 사라졌어요. 항상 false예요.", example = "false", deprecated = true)
        boolean canAdOpen,
        @Schema(description = "[deprecated] 상자는 사라졌어요. 항상 false예요.", example = "false", deprecated = true)
        boolean charging,
        @Schema(description = "[deprecated] 상자는 사라졌어요. 항상 null이에요.", nullable = true, deprecated = true)
        Instant nextRechargeAt,
        @Schema(description = "[deprecated] `shardProgressTapCount` 필드와 같은 값이에요.", example = "45", deprecated = true)
        long boxProgressTapCount,
        @Schema(description = "[deprecated] `nextShardRequiredTapCount` 필드와 같은 값이에요.", example = "100", deprecated = true)
        int nextBoxRequiredTapCount
) {
    public static KeycapBoxStatusResponse of(
            int shardBalance,
            int drawPrice,
            long shardProgressTapCount,
            int nextShardRequiredTapCount
    ) {
        return new KeycapBoxStatusResponse(
                shardBalance,
                drawPrice,
                shardBalance >= drawPrice,
                shardProgressTapCount,
                nextShardRequiredTapCount,
                0,
                false,
                false,
                false,
                null,
                shardProgressTapCount,
                nextShardRequiredTapCount
        );
    }
}
