package com.ggukmoney.beanzip.global.config.controller;

import com.ggukmoney.beanzip.global.common.ApiPaths;
import com.ggukmoney.beanzip.global.common.ApiErrorResponse;
import com.ggukmoney.beanzip.global.common.ApiResponse;
import com.ggukmoney.beanzip.global.config.OpenApiConfig;
import com.ggukmoney.beanzip.global.config.dto.response.AppConfigResponse;
import com.ggukmoney.beanzip.global.config.service.AppConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping(ApiPaths.APP_CONFIG)
@Tag(name = "App Config", description = "앱 공개 정책 설정 API")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
public class AppConfigController {

    private final AppConfigService appConfigService;

    @Operation(summary = "앱 설정 조회", description = "포인트, 키캡 상자, 부스터 정책 값을 조회합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 오류", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @GetMapping
    public ResponseEntity<ApiResponse<AppConfigResponse>> getAppConfig() {
        return ResponseEntity.ok(ApiResponse.success(appConfigService.getAppConfig()));
    }
}
