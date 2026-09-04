package com.ggukmoney.beanzip.domain.notification.controller;

import com.ggukmoney.beanzip.domain.notification.dto.request.UpdateNotificationPreferenceAgreementRequest;
import com.ggukmoney.beanzip.domain.notification.dto.request.UpdateNotificationPreferenceEnabledRequest;
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
import io.swagger.v3.oas.annotations.media.ExampleObject;
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
    @Operation(
            summary = "알림 설정 조회",
            description = "Apps-in-Toss 캠페인 코드가 설정된 알림 타입만 반환합니다. 각 항목의 templateCode를 requestNotificationAgreement에 사용합니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "알림 설정 조회 성공",
                    content = @Content(examples = @ExampleObject(value = """
                            {"success":true,"data":{"items":[{"type":"RANK_CHANGE","enabled":true,"agreementStatus":"AGREED","templateCode":"clickmoney-asfasf","promptEligible":true},{"type":"KEYCAP_BOX_OPEN_AVAILABLE","enabled":true,"agreementStatus":"AGREED","templateCode":"clickmoney-box","promptEligible":false}]}}
                            """))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "인증 필요",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            )
    })
    public ResponseEntity<ApiResponse<NotificationPreferenceListResponse>> list(
            @Parameter(hidden = true) HttpServletRequest request
    ) {
        UUID userId = AuthRequestAttributes.getRequiredUserId(request);
        return ResponseEntity.ok(ApiResponse.success(notificationPreferenceService.list(userId)));
    }

    @PatchMapping("/preferences")
    @Operation(
            summary = "알림 동의 결과 저장",
            description = "Apps-in-Toss requestNotificationAgreement의 결과를 해당 알림 타입에 저장합니다. 사용자가 나중에를 선택하면 호출하지 않습니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "알림 동의 결과 저장 성공",
                    content = @Content(examples = @ExampleObject(value = """
                            {"success":true,"data":{"type":"RANK_CHANGE","enabled":true,"agreementStatus":"AGREED","templateCode":"clickmoney-asfasf","promptEligible":true}}
                            """))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "지원하지 않는 동의 결과",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "인증 필요",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            )
    })
    public ResponseEntity<ApiResponse<NotificationPreferenceResponse>> agree(
            @Parameter(hidden = true) HttpServletRequest request,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    content = @Content(examples = @ExampleObject(value = """
                            {"type":"RANK_CHANGE","agreementResult":"newAgreement"}
                            """))
            )
            @Valid @RequestBody UpdateNotificationPreferenceAgreementRequest agreementRequest
    ) {
        UUID userId = AuthRequestAttributes.getRequiredUserId(request);
        return ResponseEntity.ok(ApiResponse.success(notificationPreferenceService.agree(userId, agreementRequest)));
    }

    @PatchMapping("/preferences/enabled")
    @Operation(
            summary = "알림 활성화 상태 변경",
            description = "동의한 알림 타입 하나의 활성화 상태를 변경합니다. 알림을 켜려면 해당 타입의 사전 동의가 필요합니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "알림 활성화 상태 변경 성공",
                    content = @Content(examples = @ExampleObject(value = """
                            {"success":true,"data":{"type":"KEYCAP_BOX_OPEN_AVAILABLE","enabled":false,"agreementStatus":"AGREED","templateCode":"clickmoney-box","promptEligible":false}}
                            """))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "인증 필요",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "409",
                    description = "알림 활성화 전 동의 필요",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            )
    })
    public ResponseEntity<ApiResponse<NotificationPreferenceResponse>> updateEnabled(
            @Parameter(hidden = true) HttpServletRequest request,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    content = @Content(examples = @ExampleObject(value = """
                            {"type":"KEYCAP_BOX_OPEN_AVAILABLE","enabled":false}
                            """))
            )
            @Valid @RequestBody UpdateNotificationPreferenceEnabledRequest updateRequest
    ) {
        UUID userId = AuthRequestAttributes.getRequiredUserId(request);
        return ResponseEntity.ok(ApiResponse.success(
                notificationPreferenceService.updateEnabled(userId, updateRequest.type(), updateRequest.enabled())
        ));
    }
}
