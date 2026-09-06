package com.ggukmoney.beanzip.domain.promotion.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Schema(description = "프로모션별 지급 현황 요약")
public record PromotionGrantSummaryResponse(
        @Schema(description = "집계 시각")
        Instant generatedAt,

        @Schema(description = "프로모션별 현황")
        List<PromotionSummary> promotions
) {

    @Schema(description = "프로모션 하나의 지급 현황")
    public record PromotionSummary(
            @Schema(description = "내부 프로모션 코드", example = "TAP_THOUSAND_COMPLETE")
            String promotionCode,

            @Schema(description = "토스에 보내는 코드. TEST_ 접두사가 남아 있으면 실지급이 아니다.",
                    example = "01M19M2GHQQCNVKJFRFEGK5Q7W")
            String tossPromotionCode,

            @Schema(description = "발급 스위치", example = "true")
            boolean issuingEnabled,

            @Schema(description = "상태별 건수", example = "{\"PENDING\":3,\"SUCCEEDED\":412}")
            Map<String, Long> statusCounts,

            @Schema(description = "실제 나간 금액(SUCCEEDED 합). 비즈 월렛 소진액과 대조한다.", example = "2060")
            long grantedAmount,

            @Schema(description = "에러코드 분포. 4112 가 보이면 예산이 마르는 중이다.",
                    example = "{\"4112\":5}")
            Map<String, Long> errorCodeCounts,

            @Schema(description = "자동 진행에서 빠진 건(hold_reason 존재)", example = "1")
            long heldCount,

            @Schema(description = "사람이 봐야 하는 건", example = "0")
            long needsReviewCount,

            @Schema(description = "가장 오래된 미결 건 경과 시간(초). 계속 커지면 폴링이 멈춘 것이다.", example = "143")
            long oldestUnsettledSeconds
    ) {
    }
}
