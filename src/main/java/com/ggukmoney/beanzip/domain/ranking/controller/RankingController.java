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
@Tag(name = "랭킹", description = "전체와 주간 랭킹을 조회합니다.")
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
            summary = "주간 랭킹 히스토리 조회",
            description = "종료된 주간 랭킹에서 내 최종 순위와 점수를 최신 시즌부터 조회합니다. "
                    + "최초 조회에는 커서를 전달하지 않고, 다음 페이지가 있으면 응답의 다음 페이지 커서를 다음 요청에 전달합니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "잘못된 커서 또는 페이지 크기", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 오류", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @GetMapping("/history")
    public ApiResponse<RankingHistoryResponse> getRankingHistory(
            @Parameter(hidden = true) HttpServletRequest request,
            @Parameter(description = "다음 페이지 조회에 사용할 커서입니다. 최초 조회에는 전달하지 않습니다.", example = "MjAyNi0wNy0yNlQxNTowMDowMFp8MTIz")
            @RequestParam(required = false) String cursor,
            @Parameter(description = "페이지 크기입니다. 생략하면 20건이며, 1 이상 100 이하만 가능합니다.", example = "20")
            @RequestParam(required = false) Integer size
    ) {
        return ApiResponse.success(rankingHistoryService.getHistory(
                AuthRequestAttributes.getRequiredUserId(request),
                cursor,
                size
        ));
    }
}
