package com.ggukmoney.beanzip.domain.promotion.controller;

import com.ggukmoney.beanzip.domain.promotion.dto.response.PromotionGrantByUserResponse;
import com.ggukmoney.beanzip.domain.promotion.dto.response.PromotionGrantRecentResponse;
import com.ggukmoney.beanzip.domain.promotion.dto.response.PromotionGrantSummaryResponse;
import com.ggukmoney.beanzip.domain.promotion.service.PromotionGrantQueryService;
import com.ggukmoney.beanzip.global.common.ApiErrorResponse;
import com.ggukmoney.beanzip.global.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * 프로모션 지급 현황 운영 조회 (BEA-272).
 *
 * <p>{@code /api/ops/**} 는 유저 세션이 아니라 {@code X-Ops-Token} 헤더로 가른다. 이 저장소에는
 * 역할 개념이 없어 유저 토큰만으로는 운영 API 를 가릴 수 없기 때문이다. 토큰이 설정되지 않으면
 * 전부 401 이다 — 설정을 빠뜨려도 열리지 않는다. {@code AuthInterceptor} 참고.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/ops/promotions/grants")
@Tag(name = "Promotion Ops", description = "프로모션 지급 현황 운영 조회 API (X-Ops-Token 필요)")
public class PromotionOpsController {

    private final PromotionGrantQueryService promotionGrantQueryService;

    @Operation(summary = "프로모션별 지급 현황 요약",
            description = "미션별 상태 건수, 누적 지급 금액, 에러코드 분포를 조회합니다. 4112 가 보이면 예산이 마르는 중입니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "운영 토큰 오류", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @GetMapping
    public ResponseEntity<ApiResponse<PromotionGrantSummaryResponse>> getSummary() {
        return ResponseEntity.ok(ApiResponse.success(promotionGrantQueryService.summary()));
    }

    @Operation(summary = "최근 지급 내역", description = "특정 프로모션의 최근 지급 건을 최신순으로 조회합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "운영 토큰 오류", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @GetMapping("/recent")
    public ResponseEntity<ApiResponse<PromotionGrantRecentResponse>> getRecent(
            @Parameter(description = "내부 프로모션 코드", example = "TAP_THOUSAND_COMPLETE")
            @RequestParam String promotionCode,
            @Parameter(description = "조회 건수 (1~100)", example = "20")
            @RequestParam(defaultValue = "20") int limit
    ) {
        return ResponseEntity.ok(ApiResponse.success(promotionGrantQueryService.recent(promotionCode, limit)));
    }

    @Operation(summary = "유저별 지급 현황",
            description = "CS 대응용. 지급 건과 함께 탭 미션의 기준값·누적값을 돌려줍니다. "
                    + "\"1,000탭 채웠는데 왜 안 왔냐\"의 답이 대부분 여기서 나옵니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "운영 토큰 오류", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @GetMapping("/by-user")
    public ResponseEntity<ApiResponse<PromotionGrantByUserResponse>> getByUser(
            @Parameter(description = "조회 대상 유저 ID")
            @RequestParam UUID userId
    ) {
        return ResponseEntity.ok(ApiResponse.success(promotionGrantQueryService.byUser(userId)));
    }
}
