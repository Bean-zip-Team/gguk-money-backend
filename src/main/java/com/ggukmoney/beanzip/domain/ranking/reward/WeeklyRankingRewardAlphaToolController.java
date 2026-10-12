package com.ggukmoney.beanzip.domain.ranking.reward;

import com.ggukmoney.beanzip.global.common.ApiErrorResponse;
import com.ggukmoney.beanzip.global.common.ApiResponse;
import com.ggukmoney.beanzip.global.config.OpenApiConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("alpha")
@ConditionalOnProperty(name = "app.ranking.weekly-reward.alpha-tools-enabled", havingValue = "true")
@RequiredArgsConstructor
@RequestMapping("/api/internal/alpha/ranking-rewards")
@Tag(
        name = "랭킹 보상 알파 도구",
        description = "주간 랭킹 보상을 지우고 다시 만드는 내부 도구예요. "
                + "`alpha` 프로필에서 `app.ranking.weekly-reward.alpha-tools-enabled` 설정이 true일 때만 켜져요. "
                + "운영 토큰이 아니라 사용자 액세스 토큰으로 인증해요."
)
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
public class WeeklyRankingRewardAlphaToolController {

    private final WeeklyRankingRewardAlphaToolService service;

    @Operation(
            summary = "주간 랭킹 보상 초기화",
            description = "시즌의 주간 랭킹 보상 기록을 모두 지워요. "
                    + "이미 수령한 보상 기록도 지워요. "
                    + "지급한 포인트는 되돌리지 않아요."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "초기화에 성공했어요."),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401", description = "인증에 실패했어요. 액세스 토큰이 없거나 유효하지 않아요.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "500", description = "시즌 코드에 해당하는 랭킹 시즌이 없어요. 전용 오류 코드가 없어서 서버 오류로 응답해요.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @PostMapping("/{seasonCode}/reset")
    public ApiResponse<Void> reset(
            @Parameter(description = "보상 기록을 지울 주간 시즌 코드예요.", example = "WEEKLY_20260720")
            @PathVariable String seasonCode
    ) {
        service.reset(seasonCode);
        return ApiResponse.success(null);
    }

    @Operation(
            summary = "주간 랭킹 보상 스냅숏 생성",
            description = "시즌의 주간 랭킹 보상을 지금 시각 기준으로 확정해요. "
                    + "최종 순위가 확정된 참가자 중 보상 정책의 순위 안에 든 사용자가 대상이에요. "
                    + "`RANK_CHANGE` 알림을 받을 수 없는 사용자에게는 보상을 만들지 않아요. "
                    + "수령 만료 시각은 지금부터 3일 뒤예요. "
                    + "만든 보상마다 보상 알림 이벤트를 발행해요. "
                    + "응답의 `data` 필드는 시즌의 보상 건수예요. "
                    + "이미 보상 기록이 있으면 새로 만들지 않고 기존 건수를 돌려줘요. "
                    + "보상 정책이 꺼져 있으면 0이에요."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "스냅숏 생성에 성공했어요."),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401", description = "인증에 실패했어요. 액세스 토큰이 없거나 유효하지 않아요.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "500", description = "시즌 코드에 해당하는 랭킹 시즌이 없거나 주간 랭킹 보상 정책을 읽을 수 없어요. 전용 오류 코드가 없어서 서버 오류로 응답해요.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @PostMapping("/{seasonCode}/snapshot")
    public ApiResponse<Integer> snapshot(
            @Parameter(description = "보상을 확정할 주간 시즌 코드예요.", example = "WEEKLY_20260720")
            @PathVariable String seasonCode
    ) {
        return ApiResponse.success(service.snapshot(seasonCode));
    }
}
