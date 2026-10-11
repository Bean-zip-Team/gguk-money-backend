package com.ggukmoney.beanzip.domain.cashout.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "출금 신청 응답이에요.")
public record CashoutSubmitResponse(
        @Schema(description = "출금 요청 ID예요.", example = "11111111-1111-1111-1111-111111111111")
        UUID cashoutId,
        @Schema(description = "차감한 포인트예요. 잔액 전체가 아니라 지급액을 만드는 데 필요한 만큼만 차감해요.", example = "100")
        long pointAmount,
        @Schema(description = "전환해 지급할 Toss 포인트 금액(원)이에요.", example = "2")
        long tossPointAmount,
        @Schema(description = "출금 상태예요. `REQUESTED`는 접수, `PROCESSING`은 Toss 지급 확인 중, `SUCCEEDED`는 지급 완료, `FAILED`는 지급 실패, `CANCELED`는 취소예요. `FAILED`가 되면 차감한 포인트를 돌려줘요.", example = "PROCESSING")
        String status,
        @Schema(description = "신청 시각이에요.", example = "2026-07-15T01:00:00Z")
        Instant requestedAt
) {
}
