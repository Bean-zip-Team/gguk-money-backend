package com.ggukmoney.beanzip.global.config.controller;

import com.ggukmoney.beanzip.global.common.ApiPaths;
import com.ggukmoney.beanzip.global.common.ApiResponse;
import com.ggukmoney.beanzip.global.config.dto.response.AppConfigResponse;
import com.ggukmoney.beanzip.global.config.service.AppConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping(ApiPaths.APP_CONFIG)
public class AppConfigController {

    private final AppConfigService appConfigService;

    @GetMapping
    public ResponseEntity<ApiResponse<AppConfigResponse>> getAppConfig() {
        return ResponseEntity.ok(ApiResponse.success(appConfigService.getAppConfig()));
    }
}
