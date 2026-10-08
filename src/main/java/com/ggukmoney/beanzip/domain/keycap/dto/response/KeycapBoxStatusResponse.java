package com.ggukmoney.beanzip.domain.keycap.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/**
 * 조각 지갑 상태 (BEA-329). 상자 시절 필드는 구버전 앱이 깨지지 않도록 중립값으로 남긴다.
 */
@Schema(description = "키캡 조각 상태 응답")
public record KeycapBoxStatusResponse(
        @Schema(description = "현재 보유한 조각 수", example = "12")
        int shardBalance,
        @Schema(description = "뽑기 1회에 드는 조각 수", example = "5")
        int drawPrice,
        @Schema(description = "조각을 보유하고 있으며 뽑기 가격 이상인지 여부", example = "true")
        boolean canDraw,
        @Schema(description = "현재 조각 진행 탭 수", example = "45")
        long shardProgressTapCount,
        @Schema(description = "다음 조각 획득 필요 탭 수", example = "100")
        int nextShardRequiredTapCount,
        @Schema(description = "[deprecated] 상자는 사라졌다. 항상 0", example = "0")
        int boxBalance,
        @Schema(description = "[deprecated] 항상 false", example = "false")
        boolean canFreeOpen,
        @Schema(description = "[deprecated] 항상 false", example = "false")
        boolean canAdOpen,
        @Schema(description = "[deprecated] 항상 false", example = "false")
        boolean charging,
        @Schema(description = "[deprecated] 항상 null", example = "null")
        Instant nextRechargeAt,
        @Schema(description = "[deprecated] shardProgressTapCount 와 같다", example = "45")
        long boxProgressTapCount,
        @Schema(description = "[deprecated] nextShardRequiredTapCount 와 같다", example = "100")
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
