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
@Tag(name = "Mission", description = "미션 목록 API")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
public class MissionRewardController {

    private final MissionFeedService missionFeedService;

    @Operation(summary = "미션 보상 목록 조회",
            description = "상태별 보상 이력을 조회합니다. EXPIRED 로 조회하면 받지 못한 채 소멸한 보상이 나옵니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 오류", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @GetMapping
    public ResponseEntity<ApiResponse<MissionRewardListResponse>> getRewards(
            @Parameter(description = "조회할 상태") @RequestParam(name = "status", defaultValue = "CLAIMABLE")
            MissionRewardListResponse.RewardStatus status,
            @Parameter(hidden = true) HttpServletRequest httpServletRequest
    ) {
        return ResponseEntity.ok(ApiResponse.success(missionFeedService.rewardHistory(
                AuthRequestAttributes.getRequiredUserId(httpServletRequest),
                status
        )));
    }

    @Operation(summary = "미션 보상 수령",
            description = "달성한 보상 하나를 받습니다. 이미 받았으면 409, 자정을 넘겨 소멸했으면 410 입니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "수령 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 오류", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "보상 없음", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "이미 수령", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "410", description = "소멸한 보상", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @PostMapping("/{rewardId}/claim")
    public ResponseEntity<ApiResponse<MissionRewardClaimResponse>> claim(
            @PathVariable("rewardId") UUID rewardId,
            @Parameter(hidden = true) HttpServletRequest httpServletRequest
    ) {
        return ResponseEntity.ok(ApiResponse.success(missionFeedService.claim(
                AuthRequestAttributes.getRequiredUserId(httpServletRequest),
                rewardId
        )));
    }

    @Operation(summary = "미션 보상 일괄 수령",
            description = "받을 수 있는 보상을 한 번에 받습니다. 이미 소멸한 건은 건너뛰고 나머지를 지급합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "수령 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 오류", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
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
