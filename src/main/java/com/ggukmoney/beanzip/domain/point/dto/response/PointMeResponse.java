package com.ggukmoney.beanzip.domain.point.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "내 포인트 상태 응답이에요.")
public record PointMeResponse(
        @Schema(description = "현재 포인트 잔액이에요.", example = "15")
        long balance,
        @Schema(description = "누적 적립 포인트예요. 출금 실패로 환불받은 포인트도 여기에 더해져요.", example = "100")
        long lifetimeEarned,
        @Schema(description = "누적 사용 포인트예요.", example = "85")
        long lifetimeSpent,
        @Schema(description = "출금 가능 여부예요. 잔액이 최소 출금 포인트 이상이면 `true`예요.", example = "false")
        boolean cashoutEligible,
        @Schema(description = "출금에 필요한 최소 포인트예요.", example = "50")
        int minimumPoint,
        @Schema(description = "잔액을 원화로 환산한 예상 금액(원)이에요. 잔액에 전환 비율을 곱한 뒤 소수점 아래를 버려요.", example = "0")
        long estimatedKrw
) {
}
