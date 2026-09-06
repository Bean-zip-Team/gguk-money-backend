package com.ggukmoney.beanzip.domain.promotion.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.UUID;

/**
 * CS 대응용 지목 조회.
 *
 * <p>탭 미션의 baseline 과 누적값을 함께 담는다. "1,000탭 채웠는데 왜 안 왔냐"의 답이
 * 대부분 여기서 나온다 — 순증이 임계 미만인지, baseline 이 아직 없는지(스위치를 켜기 전
 * 유저인지)가 이 두 값으로 갈린다.
 */
@Schema(description = "유저별 프로모션 지급 현황")
public record PromotionGrantByUserResponse(
        @Schema(description = "조회 대상 유저")
        UUID userId,

        @Schema(description = "이 유저의 지급 건. 프로모션당 최대 1건이다.")
        List<PromotionGrantItemResponse> grants,

        @Schema(description = "탭 미션 기준값. null 이면 아직 커트오프에 고정되지 않았다.", example = "3200")
        Long tapPromotionBaseline,

        @Schema(description = "누적 유효 탭 수", example = "4150")
        Long cumulativeValidTapCount,

        @Schema(description = "커트오프 이후 순증 탭 수. baseline 이 없으면 null.", example = "950")
        Long netTapCountSinceBaseline
) {
}
