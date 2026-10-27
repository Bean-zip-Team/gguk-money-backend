package com.ggukmoney.beanzip.domain.booster.controller;

import com.ggukmoney.beanzip.domain.booster.dto.request.BoosterActivateRequest;
import com.ggukmoney.beanzip.domain.booster.dto.response.BoosterActivateResponse;
import com.ggukmoney.beanzip.domain.booster.dto.response.BoosterStatusResponse;
import com.ggukmoney.beanzip.domain.booster.service.BoosterGrantService;
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
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/boosters")
@Tag(name = "Boosters", description = "부스터를 활성화하고 현재 상태를 조회하는 API예요.")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
public class BoosterController {

    private final BoosterGrantService boosterGrantService;

    @Operation(summary = "부스터 활성화", description = "Toss IntegratedAd의 `userEarnedReward` 이벤트를 받은 뒤 광고 그룹 ID로 부스터를 활성화해요. 부스터는 `tap.booster.durationSeconds` 설정 시간(기본 300초) 동안 유지돼요.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "활성화에 성공했어요."),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "요청 값이 올바르지 않아요. `adGroupId` 필드가 없거나 비어 있는 경우예요.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증에 실패했어요. 액세스 토큰이 없거나 유효하지 않아요.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "이미 활성화된 부스터가 있어요. 지금 부스터가 끝난 뒤에 다시 활성화할 수 있어요.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "429", description = "일일 부스터 한도를 넘었어요. 최근 `tap.booster.limitWindowSeconds` 설정 기간(기본 86400초) 동안 시작한 부스터 수가 `tap.booster.dailyLimit` 설정 값(기본 3회)에 닿았어요. 달력 날짜 기준이 아니에요.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @PostMapping("/activate")
    public ResponseEntity<ApiResponse<BoosterActivateResponse>> activate(
            @Valid @RequestBody BoosterActivateRequest request,
            @Parameter(hidden = true) HttpServletRequest httpServletRequest
    ) {
        var userId = AuthRequestAttributes.getRequiredUserId(httpServletRequest);
        return ResponseEntity.ok(ApiResponse.success(boosterGrantService.activate(userId, request.adGroupId())));
    }

    @Operation(summary = "현재 부스터 상태 조회", description = "지금 켜진 부스터의 배율과 남은 시간, 남은 활성화 횟수를 조회해요. 켜진 부스터가 없어도 200으로 응답하고 `active` 필드가 false예요.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회에 성공했어요."),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증에 실패했어요. 액세스 토큰이 없거나 유효하지 않아요.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @GetMapping("/current")
    public ResponseEntity<ApiResponse<BoosterStatusResponse>> current(@Parameter(hidden = true) HttpServletRequest httpServletRequest) {
        var userId = AuthRequestAttributes.getRequiredUserId(httpServletRequest);
        return ResponseEntity.ok(ApiResponse.success(boosterGrantService.getCurrentStatus(userId)));
    }
}
