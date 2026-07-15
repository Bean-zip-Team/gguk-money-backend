package com.ggukmoney.beanzip.domain.point.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "내 포인트 상태 응답")
public record PointMeResponse(
        @Schema(description = "현재 포인트 잔액", example = "15")
        long balance,
        @Schema(description = "누적 적립 포인트", example = "100")
        long lifetimeEarned,
        @Schema(description = "누적 사용 포인트", example = "85")
        long lifetimeSpent,
        @Schema(description = "출금 가능 여부", example = "true")
        boolean cashoutEligible,
        @Schema(description = "최소 출금 가능 포인트", example = "10")
        int minimumPoint,
        @Schema(description = "예상 원화 환산 금액", example = "10")
        long estimatedKrw
) {
}
