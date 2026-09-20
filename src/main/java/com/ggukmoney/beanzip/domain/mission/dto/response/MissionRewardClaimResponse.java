package com.ggukmoney.beanzip.domain.mission.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 데일리 미션 보상 수령 결과 (BEA-299).
 *
 * <p>일괄 수령도 같은 모양을 쓴다. 화면 상단의 "받을 보상" 합계를 즉시 줄이려면 몇 건을 얼마에
 * 받았는지가 필요하고, 적립 후 잔액을 함께 내려주면 포인트 조회를 한 번 더 하지 않아도 된다.
 */
@Schema(description = "미션 보상 수령 결과")
public record MissionRewardClaimResponse(
        @Schema(description = "수령한 보상 건수", example = "2")
        int claimedCount,

        @Schema(description = "수령한 포인트 합계", example = "110")
        long claimedPointAmount,

        @Schema(description = "수령 후 포인트 잔액", example = "1380")
        long pointBalance
) {
}
