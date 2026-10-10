package com.ggukmoney.beanzip.domain.mission.controller;

import com.ggukmoney.beanzip.domain.mission.dto.response.MissionRewardClaimResponse;
import com.ggukmoney.beanzip.domain.mission.dto.response.MissionRewardListResponse;
import com.ggukmoney.beanzip.domain.mission.service.MissionFeedService;
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
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * 데일리 미션 보상 수령 (BEA-299).
 *
 * <p>달성해도 자동으로 지급되지 않는다. 유저가 직접 받아야 하고, 받지 않은 보상은 자정에 소멸한다.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/missions/rewards")
@Tag(name = "Mission", description = "미션 목록을 조회하고 미션 보상을 받는 API예요.")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
public class MissionRewardController {

    private final MissionFeedService missionFeedService;

    @Operation(summary = "미션 보상 목록 조회",
            description = "상태별 보상 이력을 조회해요. 상태를 `EXPIRED`로 주면 받지 못한 채 소멸한 보상이 나와요. "
                    + "`CLAIMABLE`로 조회하면 자정이 지나 이미 받을 수 없는 보상은 빼요.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회에 성공했어요."),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증에 실패했어요. 액세스 토큰이 없거나 유효하지 않아요.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @GetMapping
    public ResponseEntity<ApiResponse<MissionRewardListResponse>> getRewards(
            @Parameter(description = "조회할 보상 상태예요. 기본값은 `CLAIMABLE`이에요. "
                    + "`CLAIMABLE`은 받을 수 있음, `CLAIMED`는 이미 받음, `EXPIRED`는 받지 못한 채 소멸함이에요.")
            @RequestParam(name = "status", defaultValue = "CLAIMABLE")
            MissionRewardListResponse.RewardStatus status,
            @Parameter(hidden = true) HttpServletRequest httpServletRequest
    ) {
        return ResponseEntity.ok(ApiResponse.success(missionFeedService.rewardHistory(
                AuthRequestAttributes.getRequiredUserId(httpServletRequest),
                status
        )));
    }

    @Operation(summary = "미션 보상 수령",
            description = "달성한 보상 하나를 받아요. 받기 전에 오늘의 미션 달성 여부를 다시 판정해요. "
                    + "이미 받은 보상이면 409를 돌려줘요. 자정을 넘겨 소멸한 보상이면 410을 돌려줘요.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "수령에 성공했어요."),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증에 실패했어요. 액세스 토큰이 없거나 유효하지 않아요.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "보상을 찾을 수 없어요. 존재하지 않는 보상이거나 다른 유저의 보상이에요.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "이미 받은 보상이에요.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "410", description = "소멸한 보상이에요. 받지 않은 데일리 보상은 자정에 소멸해요.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @PostMapping("/{rewardId}/claim")
    public ResponseEntity<ApiResponse<MissionRewardClaimResponse>> claim(
            @Parameter(description = "받을 보상 ID예요. 미션 목록이나 보상 목록 응답의 `rewardId` 필드 값을 넣어요.")
            @PathVariable("rewardId") UUID rewardId,
            @Parameter(hidden = true) HttpServletRequest httpServletRequest
    ) {
        return ResponseEntity.ok(ApiResponse.success(missionFeedService.claim(
                AuthRequestAttributes.getRequiredUserId(httpServletRequest),
                rewardId
        )));
    }

    @Operation(summary = "미션 보상 일괄 수령",
            description = "받을 수 있는 보상을 한 번에 받아요. 받기 전에 오늘의 미션 달성 여부를 다시 판정해요. "
                    + "단발성 미션 보상도 함께 받아요. 이미 소멸한 건은 건너뛰고 나머지를 지급해요.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "수령에 성공했어요."),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증에 실패했어요. 액세스 토큰이 없거나 유효하지 않아요.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @PostMapping("/claim-all")
    public ResponseEntity<ApiResponse<MissionRewardClaimResponse>> claimAll(
            @Parameter(hidden = true) HttpServletRequest httpServletRequest
    ) {
        return ResponseEntity.ok(ApiResponse.success(missionFeedService.claimAll(
                AuthRequestAttributes.getRequiredUserId(httpServletRequest)
        )));
    }
}
