package com.ggukmoney.beanzip.domain.onboarding.controller;

import com.ggukmoney.beanzip.domain.onboarding.dto.request.OnboardingKeycapBoxOpenRequest;
import com.ggukmoney.beanzip.domain.onboarding.dto.response.OnboardingKeycapBoxOpenResponse;
import com.ggukmoney.beanzip.domain.onboarding.service.OnboardingKeycapBoxOpenService;
import com.ggukmoney.beanzip.global.common.ApiPaths;
import com.ggukmoney.beanzip.global.common.ApiErrorResponse;
import com.ggukmoney.beanzip.global.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping(ApiPaths.ONBOARDING_KEYCAP_BOXES)
@Tag(name = "Onboarding Keycap Boxes", description = "비로그인 온보딩 키캡 상자 API")
public class OnboardingKeycapBoxController {

    private final OnboardingKeycapBoxOpenService onboardingKeycapBoxOpenService;

    @Operation(summary = "온보딩 키캡 상자 개봉", description = "비로그인 사용자의 온보딩 탭 이벤트를 검증하고 온보딩 보상을 개봉합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "개봉 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "탭 이벤트 또는 요청 값 오류", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "탭 세션 재사용 또는 보상 없음", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "410", description = "온보딩 보상 시도 만료", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @PostMapping("/open")
    public ResponseEntity<ApiResponse<OnboardingKeycapBoxOpenResponse>> open(
            @Valid @RequestBody OnboardingKeycapBoxOpenRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(onboardingKeycapBoxOpenService.open(request)));
    }
}
