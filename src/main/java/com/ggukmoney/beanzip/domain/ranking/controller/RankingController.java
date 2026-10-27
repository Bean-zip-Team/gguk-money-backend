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
@Tag(name = "랭킹", description = "진행 중인 주간 랭킹과 지난 주간 랭킹 기록을 조회해요.")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
public class RankingController {

    private final RankingQueryService rankingQueryService;
    private final RankingHistoryService rankingHistoryService;

    @Operation(
            summary = "현재 전체 랭킹 조회",
            description = "진행 중인 주간 시즌의 전체 랭킹과 내 순위를 조회해요. 점수는 이번 시즌 동안 쌓은 유효 탭 수예요."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회에 성공했어요."),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "`limit` 값이 1 이상 100 이하 범위를 벗어났어요.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증에 실패했어요. 액세스 토큰이 없거나 유효하지 않아요.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @GetMapping("/current")
    public ApiResponse<CurrentRankingResponse> getCurrentRanking(
            @Parameter(hidden = true) HttpServletRequest request,
            @Parameter(description = "조회할 상위 랭킹 개수예요. 1 이상 100 이하만 가능해요.", example = "50")
            @RequestParam(required = false) Integer limit
    ) {
        return ApiResponse.success(rankingQueryService.getCurrentRanking(
                AuthRequestAttributes.getRequiredUserId(request),
                limit
        ));
    }

    @Operation(
            summary = "주간 랭킹 히스토리 조회",
            description = "종료된 주간 랭킹에서 내 최종 순위와 점수를 최신 시즌부터 조회해요. "
                    + "최초 조회에는 커서를 전달하지 않아요. "
                    + "다음 페이지가 있으면 응답의 `nextCursor` 값을 다음 요청에 전달해요."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회에 성공했어요."),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "커서 형식이 올바르지 않거나 페이지 크기가 1 이상 100 이하 범위를 벗어났어요.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증에 실패했어요. 액세스 토큰이 없거나 유효하지 않아요.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @GetMapping("/history")
    public ApiResponse<RankingHistoryResponse> getRankingHistory(
            @Parameter(hidden = true) HttpServletRequest request,
            @Parameter(description = "다음 페이지 조회에 쓸 커서예요. 최초 조회에는 전달하지 않아요.", example = "MjAyNi0wNy0yNlQxNTowMDowMFp8MTIz")
            @RequestParam(required = false) String cursor,
            @Parameter(description = "페이지 크기예요. 생략하면 20건이에요. 1 이상 100 이하만 가능해요.", example = "20")
            @RequestParam(required = false) Integer size
    ) {
        return ApiResponse.success(rankingHistoryService.getHistory(
                AuthRequestAttributes.getRequiredUserId(request),
                cursor,
                size
        ));
    }
}
