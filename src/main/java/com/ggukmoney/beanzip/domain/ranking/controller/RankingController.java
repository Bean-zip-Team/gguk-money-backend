package com.ggukmoney.beanzip.domain.ranking.controller;

import com.ggukmoney.beanzip.domain.ranking.dto.response.CurrentRankingResponse;
import com.ggukmoney.beanzip.domain.ranking.dto.response.RankingHistoryResponse;
import com.ggukmoney.beanzip.domain.ranking.service.RankingHistoryService;
import com.ggukmoney.beanzip.domain.ranking.service.RankingQueryService;
import com.ggukmoney.beanzip.global.common.ApiErrorResponse;
import com.ggukmoney.beanzip.global.common.ApiResponse;
import com.ggukmoney.beanzip.global.config.OpenApiConfig;
import com.ggukmoney.beanzip.global.interceptor.AuthRequestAttributes;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/rankings")
@Tag(name = "Rankings", description = "전체 랭킹 조회 API")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
public class RankingController {

    private final RankingQueryService rankingQueryService;
    private final RankingHistoryService rankingHistoryService;

    @Operation(
            summary = "현재 전체 랭킹 조회",
            description = "누적 유효 탭 수 기준 현재 전체 랭킹과 내 랭킹 정보를 조회합니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "잘못된 limit", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 오류", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @GetMapping("/current")
    public ApiResponse<CurrentRankingResponse> getCurrentRanking(
            @Parameter(hidden = true) HttpServletRequest request,
            @Parameter(description = "조회할 상위 랭킹 개수. 1 이상 100 이하", example = "50")
            @RequestParam(required = false) Integer limit
    ) {
        return ApiResponse.success(rankingQueryService.getCurrentRanking(
                AuthRequestAttributes.getRequiredUserId(request),
                limit
        ));
    }

    @Operation(
            summary = "Weekly ranking history",
            description = "Returns my closed weekly ranking history."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "잘못된 cursor 또는 size", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 오류", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @GetMapping("/history")
    public ApiResponse<RankingHistoryResponse> getRankingHistory(
            @Parameter(hidden = true) HttpServletRequest request,
            @Parameter(description = "다음 페이지 cursor", example = "MjAyNi0wNy0yNlQxNTowMDowMFp8MTIz")
            @RequestParam(required = false) String cursor,
            @Parameter(description = "조회할 히스토리 개수. 1 이상 100 이하", example = "20")
            @RequestParam(required = false) Integer size
    ) {
        return ApiResponse.success(rankingHistoryService.getHistory(
                AuthRequestAttributes.getRequiredUserId(request),
                cursor,
                size
        ));
    }
}
