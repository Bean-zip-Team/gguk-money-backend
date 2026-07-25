package com.ggukmoney.beanzip.domain.notification.controller;

import com.ggukmoney.beanzip.domain.notification.dto.request.NotificationAgreementRequest;
import com.ggukmoney.beanzip.domain.notification.dto.request.UpdateGlobalNotificationEnabledRequest;
import com.ggukmoney.beanzip.domain.notification.dto.response.NotificationPreferenceListResponse;
import com.ggukmoney.beanzip.domain.notification.dto.response.NotificationPreferenceResponse;
import com.ggukmoney.beanzip.domain.notification.service.NotificationPreferenceService;
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
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/notifications")
@Tag(name = "Notifications", description = "Apps-in-Toss Smart Message 알림 설정 API")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
public class NotificationPreferenceController {

    private final NotificationPreferenceService notificationPreferenceService;

    @GetMapping("/preferences")
    @Operation(summary = "알림 설정 조회", description = "전역 동의 상태와 타입별 Smart Message 알림 설정을 조회합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 오류", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<ApiResponse<NotificationPreferenceListResponse>> list(
            @Parameter(hidden = true) HttpServletRequest request
    ) {
        UUID userId = AuthRequestAttributes.getRequiredUserId(request);
        return ResponseEntity.ok(ApiResponse.success(notificationPreferenceService.list(userId)));
    }

    @PatchMapping("/agreement")
    @Operation(summary = "전역 알림 동의 결과 저장", description = "Apps-in-Toss requestNotificationAgreement 결과를 5개 알림 타입에 일괄 반영합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "동의 결과 저장 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "지원하지 않는 동의 결과", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 오류", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<ApiResponse<NotificationPreferenceListResponse>> agree(
            @Parameter(hidden = true) HttpServletRequest request,
            @Valid @RequestBody NotificationAgreementRequest agreementRequest
    ) {
        UUID userId = AuthRequestAttributes.getRequiredUserId(request);
        return ResponseEntity.ok(ApiResponse.success(notificationPreferenceService.agree(userId, agreementRequest)));
    }

    @PatchMapping("/preferences/enabled")
    @Operation(summary = "전역 알림 활성화 변경", description = "모든 Smart Message 알림 타입을 함께 켜거나 끕니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "활성화 상태 변경 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 오류", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "전역 알림 동의 필요", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<ApiResponse<NotificationPreferenceListResponse>> updateEnabled(
            @Parameter(hidden = true) HttpServletRequest request,
            @RequestBody UpdateGlobalNotificationEnabledRequest updateRequest
    ) {
        UUID userId = AuthRequestAttributes.getRequiredUserId(request);
        return ResponseEntity.ok(ApiResponse.success(notificationPreferenceService.updateGlobalEnabled(userId, updateRequest.enabled())));
    }
}
