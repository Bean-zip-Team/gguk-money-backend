package com.ggukmoney.beanzip.global.config.controller;

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
@RequestMapping("/api/app-config")
@Tag(name = "App Config", description = "앱에 공개하는 정책 설정을 조회하는 API예요.")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
public class AppConfigController {

    private final AppConfigService appConfigService;

    @Operation(summary = "앱 설정 조회", description = "포인트, 키캡 조각, 부스터 정책 값을 조회해요. 서버는 정책 값을 60초마다 다시 읽어요. 그래서 바뀐 값은 최대 60초 늦게 반영돼요.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회에 성공했어요."),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증에 실패했어요. 액세스 토큰이 없거나 유효하지 않아요.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @GetMapping
    public ResponseEntity<ApiResponse<AppConfigResponse>> getAppConfig() {
        return ResponseEntity.ok(ApiResponse.success(appConfigService.getAppConfig()));
    }
}
