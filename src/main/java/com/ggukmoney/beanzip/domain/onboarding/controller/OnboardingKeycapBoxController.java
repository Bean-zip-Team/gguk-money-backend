package com.ggukmoney.beanzip.domain.onboarding.controller;

import com.ggukmoney.beanzip.domain.onboarding.dto.request.OnboardingKeycapBoxOpenRequest;
import com.ggukmoney.beanzip.domain.onboarding.dto.response.OnboardingKeycapBoxOpenResponse;
import com.ggukmoney.beanzip.domain.onboarding.service.OnboardingKeycapBoxOpenService;
import com.ggukmoney.beanzip.global.common.ApiPaths;
import com.ggukmoney.beanzip.global.common.ApiResponse;
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
public class OnboardingKeycapBoxController {

    private final OnboardingKeycapBoxOpenService onboardingKeycapBoxOpenService;

    @PostMapping("/open")
    public ResponseEntity<ApiResponse<OnboardingKeycapBoxOpenResponse>> open(
            @Valid @RequestBody OnboardingKeycapBoxOpenRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(onboardingKeycapBoxOpenService.open(request)));
    }
}
