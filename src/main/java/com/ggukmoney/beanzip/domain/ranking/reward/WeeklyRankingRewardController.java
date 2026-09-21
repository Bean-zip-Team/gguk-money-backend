package com.ggukmoney.beanzip.domain.ranking.reward;

import com.ggukmoney.beanzip.domain.ranking.reward.dto.LatestWeeklyRankingRewardResponse;
import com.ggukmoney.beanzip.domain.ranking.reward.dto.MyWeeklyRankingRewardResponse;
import com.ggukmoney.beanzip.global.common.ApiErrorResponse;
import com.ggukmoney.beanzip.global.common.ApiResponse;
import com.ggukmoney.beanzip.global.config.OpenApiConfig;
import com.ggukmoney.beanzip.global.interceptor.AuthRequestAttributes;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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
@Tag(name = "랭킹 보상", description = "주간 랭킹 보상 결과와 수령을 처리합니다.")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
public class WeeklyRankingRewardController {

    private final WeeklyRankingRewardQueryService queryService;
    private final WeeklyRankingRewardClaimService claimService;

    @Operation(summary = "가장 최근 종료된 주간 랭킹 보상 결과 조회")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 오류"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404", description = "종료 결과 없음",
                    content = @io.swagger.v3.oas.annotations.media.Content(
                            schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = ApiErrorResponse.class)))
    })
    @GetMapping("/latest")
    public ApiResponse<LatestWeeklyRankingRewardResponse> latest(
            @Parameter(hidden = true) HttpServletRequest request
    ) {
        return ApiResponse.success(queryService.latest(AuthRequestAttributes.getRequiredUserId(request)));
    }

    @Operation(summary = "주간 랭킹 보상 수령")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "수령 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 오류"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "순위 변동 알림 동의 필요"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "보상을 찾을 수 없음"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "410", description = "수령 기간 만료")
    })
    @PostMapping("/{rewardId}/claim")
    public ApiResponse<MyWeeklyRankingRewardResponse> claim(
            @Parameter(hidden = true) HttpServletRequest request,
            @PathVariable UUID rewardId
    ) {
        UUID userId = AuthRequestAttributes.getRequiredUserId(request);
        WeeklyRankingReward reward = claimService.claim(userId, rewardId);
        return ApiResponse.success(queryService.toMyReward(userId, reward));
    }
}
