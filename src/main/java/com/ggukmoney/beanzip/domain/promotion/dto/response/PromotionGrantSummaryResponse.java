package com.ggukmoney.beanzip.domain.promotion.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Schema(description = "프로모션별 지급 현황 요약이에요.")
public record PromotionGrantSummaryResponse(
        @Schema(description = "집계한 시각이에요.")
        Instant generatedAt,

        @Schema(description = "프로모션별 지급 현황 목록이에요. 발급 스위치가 꺼진 프로모션도 들어가요.")
        List<PromotionSummary> promotions
) {

    @Schema(description = "프로모션 하나의 지급 현황이에요.")
    public record PromotionSummary(
            @Schema(description = "내부 프로모션 코드예요.", example = "TAP_THOUSAND_COMPLETE")
            String promotionCode,

            @Schema(description = "토스에 보내는 프로모션 코드예요. `TEST_` 접두사가 남아 있으면 실지급이 아니에요. "
                    + "코드가 설정되지 않았으면 null이에요.",
                    example = "01M19M2GHQQCNVKJFRFEGK5Q7W")
            String tossPromotionCode,

            @Schema(description = "발급 스위치예요. false면 이 프로모션의 지급 건을 새로 만들지 않아요.", example = "true")
            boolean issuingEnabled,

            @Schema(description = "상태별 지급 건수예요. 키는 `PENDING`, `PROCESSING`, `SUCCEEDED`, `FAILED` 중 하나예요. "
                    + "건이 없는 상태도 0으로 들어가요.", example = "{\"PENDING\":3,\"SUCCEEDED\":412}")
            Map<String, Long> statusCounts,

            @Schema(description = "실제 나간 금액(토스 포인트)이에요. `SUCCEEDED` 건의 지급액을 합한 값이에요. "
                    + "비즈 월렛 소진액과 대조할 때 써요.", example = "2060")
            long grantedAmount,

            @Schema(description = "에러 코드별 건수예요. 건마다 마지막 에러 코드만 세요. `4112`가 보이면 예산이 마르는 중이에요.",
                    example = "{\"4112\":5}")
            Map<String, Long> errorCodeCounts,

            @Schema(description = "자동 진행에서 빠진 건수예요. `hold_reason` 값이 있는 건을 세요.", example = "1")
            long heldCount,

            @Schema(description = "사람이 확인해야 하는 건수예요.", example = "0")
            long needsReviewCount,

            @Schema(description = "가장 오래된 미결 건이 발급된 뒤 지난 시간(초)이에요. "
                    + "미결 건은 `PENDING`이나 `PROCESSING` 상태인 건이에요. 미결 건이 없으면 0이에요. "
                    + "계속 커지면 폴링이 멈춘 거예요.", example = "143")
            long oldestUnsettledSeconds
    ) {
    }
}
