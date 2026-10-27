package com.ggukmoney.beanzip.domain.keycap.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "자동 클릭 정산의 확정 결과예요. 같은 멱등키로 다시 호출하면 추가 지급 없이 최초 결과를 돌려줘요. 잔액과 시각도 최초 정산 당시 값이에요.")
public record KeycapPassiveSettleResponse(
        @Schema(description = "이번에 정산한 자동 클릭 수예요. 포인트 지급용 탭 한도를 넘는 클릭도 조각과 랭킹에는 반영해요.", example = "60")
        int autoClicksGranted,
        @Schema(description = "이번 자동 클릭 배치로 지급한 포인트예요. 기본 지급 기회는 배치당 한 번이며 포인트 크리티컬 적용 후 일일 상한으로 잘라요.", example = "1")
        int pointsAwarded,
        @Schema(description = "이번 정산으로 나온 조각 수예요. 각 드롭의 기본 1개에 조각 크리티컬을 적용해요.", example = "2")
        int shardsDropped,
        @Schema(description = "이번 정산 직후 포인트 잔액이에요.", example = "15")
        long balance,
        @Schema(description = "이번 정산 직후 조각 잔액이에요.", example = "12")
        int shardBalance,
        @Schema(description = "미수령 자동 클릭이 적립 상한을 초과해 초과분을 버렸는지 여부예요. 정확히 상한과 같으면 false예요.", example = "false")
        boolean capped,
        @Schema(description = "이번 정산의 서버 기준 시각이에요. 같은 멱등키의 재응답에서도 최초 정산 시각을 유지해요.", example = "2026-10-12T00:00:00Z")
        Instant settledAt
) {}
