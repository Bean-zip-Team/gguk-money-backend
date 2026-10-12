package com.ggukmoney.beanzip.domain.ranking.reward;

import com.ggukmoney.beanzip.domain.ranking.reward.dto.LatestWeeklyRankingRewardResponse;
import com.ggukmoney.beanzip.domain.ranking.reward.dto.MyWeeklyRankingRewardResponse;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/rankings/rewards")
@Tag(name = "랭킹 보상", description = "주간 랭킹 보상 결과와 수령을 처리해요.")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
public class WeeklyRankingRewardController {

    private final WeeklyRankingRewardQueryService queryService;
    private final WeeklyRankingRewardClaimService claimService;

    @Operation(
            summary = "가장 최근 종료된 주간 랭킹 보상 결과 조회",
            description = "가장 최근에 종료된 주간 시즌의 보상 수상자 목록과 내 보상을 조회해요."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회에 성공했어요."),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401", description = "인증에 실패했어요. 액세스 토큰이 없거나 유효하지 않아요.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404", description = "종료된 주간 랭킹 결과가 없어요.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @GetMapping("/latest")
    public ApiResponse<LatestWeeklyRankingRewardResponse> latest(
            @Parameter(hidden = true) HttpServletRequest request
    ) {
        return ApiResponse.success(queryService.latest(AuthRequestAttributes.getRequiredUserId(request)));
    }

    @Operation(
            summary = "주간 랭킹 보상 수령",
            description = "내 주간 랭킹 보상을 수령하고 포인트를 지급해요. "
                    + "이미 수령한 보상이면 다시 지급하지 않고 현재 상태를 돌려줘요. "
                    + "사내 계정은 포인트를 지급하지 않고 수령 처리만 해요."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "수령에 성공했어요."),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401", description = "인증에 실패했어요. 액세스 토큰이 없거나 유효하지 않아요.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404", description = "보상을 찾을 수 없어요. 보상 ID가 없거나 내 보상이 아니에요.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "409", description = "수령 기간이 만료됐어요.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "503", description = "사내 계정 지급 정책을 확인할 수 없어요.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @PostMapping("/{rewardId}/claim")
    public ApiResponse<MyWeeklyRankingRewardResponse> claim(
            @Parameter(hidden = true) HttpServletRequest request,
            @Parameter(description = "수령할 보상 ID예요.", example = "22222222-2222-2222-2222-222222222222")
            @PathVariable UUID rewardId
    ) {
        UUID userId = AuthRequestAttributes.getRequiredUserId(request);
        WeeklyRankingReward reward = claimService.claim(userId, rewardId);
        return ApiResponse.success(queryService.toMyReward(userId, reward));
    }

    @Operation(
            summary = "주간 랭킹 수상 결과 열람 기록",
            description = "내 수상 결과를 열람했다고 기록해요. "
                    + "처음 열람한 시각만 남기고 다시 호출해도 바꾸지 않아요. "
                    + "기록한 뒤에는 결과 조회 응답의 `hasUnreadWinner` 값이 false가 돼요."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "열람 기록에 성공했어요."),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401", description = "인증에 실패했어요. 액세스 토큰이 없거나 유효하지 않아요.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404", description = "보상을 찾을 수 없어요. 보상 ID가 없거나 내 보상이 아니에요.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @PostMapping("/{rewardId}/view")
    public ApiResponse<Void> markViewed(
            @Parameter(hidden = true) HttpServletRequest request,
            @Parameter(description = "열람을 기록할 보상 ID예요.", example = "22222222-2222-2222-2222-222222222222")
            @PathVariable UUID rewardId
    ) {
        queryService.markViewed(AuthRequestAttributes.getRequiredUserId(request), rewardId);
        return ApiResponse.success(null);
    }
}
