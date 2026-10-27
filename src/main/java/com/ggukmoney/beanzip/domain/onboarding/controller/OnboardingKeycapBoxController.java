package com.ggukmoney.beanzip.domain.onboarding.controller;

import com.ggukmoney.beanzip.domain.onboarding.dto.request.OnboardingKeycapBoxOpenRequest;
import com.ggukmoney.beanzip.domain.onboarding.dto.response.OnboardingKeycapBoxOpenResponse;
import com.ggukmoney.beanzip.domain.onboarding.service.OnboardingKeycapBoxOpenService;
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
@RequestMapping("/api/onboarding/keycap-boxes")
@Tag(name = "Onboarding Keycap Boxes", description = "비로그인 온보딩 키캡 상자 API예요.")
public class OnboardingKeycapBoxController {

    private final OnboardingKeycapBoxOpenService onboardingKeycapBoxOpenService;

    @Operation(summary = "온보딩 키캡 상자 개봉", description = "비로그인 사용자의 온보딩 탭 이벤트를 검증하고 온보딩 보상을 개봉해요. 인증 없이 호출해요. 같은 `tapSessionId`로 같은 요청을 다시 보내면 처음 결과를 그대로 돌려줘요.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "개봉에 성공했어요."),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "탭 이벤트나 요청 값이 올바르지 않아요.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "같은 탭 세션을 다른 요청에 재사용했거나 지급할 보상 키캡이 없어요.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "410", description = "온보딩 보상 시도가 만료됐어요.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @PostMapping("/open")
    public ResponseEntity<ApiResponse<OnboardingKeycapBoxOpenResponse>> open(
            @Valid @RequestBody OnboardingKeycapBoxOpenRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(onboardingKeycapBoxOpenService.open(request)));
    }
}
