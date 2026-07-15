package com.ggukmoney.beanzip.domain.booster.controller;

import com.ggukmoney.beanzip.domain.booster.dto.request.BoosterActivateRequest;
import com.ggukmoney.beanzip.domain.booster.dto.response.BoosterActivateResponse;
import com.ggukmoney.beanzip.domain.booster.dto.response.BoosterStatusResponse;
import com.ggukmoney.beanzip.domain.booster.service.BoosterGrantService;
import com.ggukmoney.beanzip.global.common.ApiPaths;
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
@RequestMapping(ApiPaths.BOOSTERS)
@Tag(name = "Boosters", description = "부스터 활성화 및 상태 API")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
public class BoosterController {

    private final BoosterGrantService boosterGrantService;

    @Operation(summary = "부스터 활성화", description = "광고 시청 식별자로 부스터를 활성화합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "활성화 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "adViewId 누락 또는 요청 값 오류", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 오류", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "이미 활성화된 부스터", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "429", description = "일일 부스터 한도 초과", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @PostMapping("/activate")
    public ResponseEntity<ApiResponse<BoosterActivateResponse>> activate(
            @Valid @RequestBody BoosterActivateRequest request,
            @Parameter(hidden = true) HttpServletRequest httpServletRequest
    ) {
        var userId = AuthRequestAttributes.getRequiredUserId(httpServletRequest);
        return ResponseEntity.ok(ApiResponse.success(boosterGrantService.activate(userId, request.adViewId())));
    }

    @Operation(summary = "현재 부스터 상태 조회")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 오류", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @GetMapping("/current")
    public ResponseEntity<ApiResponse<BoosterStatusResponse>> current(@Parameter(hidden = true) HttpServletRequest httpServletRequest) {
        var userId = AuthRequestAttributes.getRequiredUserId(httpServletRequest);
        return ResponseEntity.ok(ApiResponse.success(boosterGrantService.getCurrentStatus(userId)));
    }
}
