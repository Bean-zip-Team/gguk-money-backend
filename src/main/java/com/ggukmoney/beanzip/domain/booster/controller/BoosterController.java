package com.ggukmoney.beanzip.domain.booster.controller;

import com.ggukmoney.beanzip.domain.booster.dto.request.BoosterActivateRequest;
import com.ggukmoney.beanzip.domain.booster.dto.response.BoosterActivateResponse;
import com.ggukmoney.beanzip.domain.booster.dto.response.BoosterStatusResponse;
import com.ggukmoney.beanzip.domain.booster.service.BoosterGrantService;
import com.ggukmoney.beanzip.global.common.ApiPaths;
import com.ggukmoney.beanzip.global.common.ApiResponse;
import com.ggukmoney.beanzip.global.interceptor.AuthRequestAttributes;
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
public class BoosterController {

    private final BoosterGrantService boosterGrantService;

    @PostMapping("/activate")
    public ResponseEntity<ApiResponse<BoosterActivateResponse>> activate(
            @Valid @RequestBody BoosterActivateRequest request,
            HttpServletRequest httpServletRequest
    ) {
        var userId = AuthRequestAttributes.getRequiredUserId(httpServletRequest);
        return ResponseEntity.ok(ApiResponse.success(boosterGrantService.activate(userId, request.adViewId())));
    }

    @GetMapping("/current")
    public ResponseEntity<ApiResponse<BoosterStatusResponse>> current(HttpServletRequest httpServletRequest) {
        var userId = AuthRequestAttributes.getRequiredUserId(httpServletRequest);
        return ResponseEntity.ok(ApiResponse.success(boosterGrantService.getCurrentStatus(userId)));
    }
}
