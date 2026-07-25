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
@Tag(name = "Notifications", description = "Apps-in-Toss Smart Message notification preferences")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
public class NotificationPreferenceController {

    private final NotificationPreferenceService notificationPreferenceService;

    @GetMapping("/preferences")
    @Operation(
            summary = "List notification preferences",
            description = "Returns only notification types with a configured Apps-in-Toss campaign code. Use each item templateCode with requestNotificationAgreement."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Preferences returned",
                    content = @Content(examples = @ExampleObject(value = """
                            {"success":true,"data":{"items":[{"type":"RANK_CHANGE","enabled":true,"agreementStatus":"AGREED","templateCode":"clickmoney-asfasf","promptEligible":true}]}}
                            """))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "Authentication required",
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
            summary = "Save notification agreement result",
            description = "Saves one notification type result from Apps-in-Toss requestNotificationAgreement. The client does not call this endpoint when the user chooses later."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Agreement result saved",
                    content = @Content(examples = @ExampleObject(value = """
                            {"success":true,"data":{"type":"RANK_CHANGE","enabled":true,"agreementStatus":"AGREED","templateCode":"clickmoney-asfasf","promptEligible":true}}
                            """))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "Invalid agreement result",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "Authentication required",
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
            summary = "Update notification preference enabled state",
            description = "Changes one agreed notification type. Enabling a notification requires prior agreement for that type."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Preference updated",
                    content = @Content(examples = @ExampleObject(value = """
                            {"success":true,"data":{"type":"BOOSTER_RECHARGED","enabled":false,"agreementStatus":"AGREED","templateCode":"clickmoney-box","promptEligible":false}}
                            """))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "Authentication required",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "409",
                    description = "Agreement required before enabling",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            )
    })
    public ResponseEntity<ApiResponse<NotificationPreferenceResponse>> updateEnabled(
            @Parameter(hidden = true) HttpServletRequest request,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    content = @Content(examples = @ExampleObject(value = """
                            {"type":"BOOSTER_RECHARGED","enabled":false}
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
