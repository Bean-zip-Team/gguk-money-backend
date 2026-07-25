package com.ggukmoney.beanzip.domain.notification.controller;

import com.ggukmoney.beanzip.domain.notification.dto.request.UpdateNotificationPreferenceRequest;
import com.ggukmoney.beanzip.domain.notification.dto.response.NotificationPreferenceListResponse;
import com.ggukmoney.beanzip.domain.notification.dto.response.NotificationPreferenceResponse;
import com.ggukmoney.beanzip.domain.notification.service.NotificationPreferenceService;
import com.ggukmoney.beanzip.global.common.ApiResponse;
import com.ggukmoney.beanzip.global.interceptor.AuthRequestAttributes;
import io.swagger.v3.oas.annotations.Parameter;
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
@RequestMapping("/api/notifications/preferences")
public class NotificationPreferenceController {

    private final NotificationPreferenceService notificationPreferenceService;

    @GetMapping
    public ResponseEntity<ApiResponse<NotificationPreferenceListResponse>> list(
            @Parameter(hidden = true) HttpServletRequest request
    ) {
        UUID userId = AuthRequestAttributes.getRequiredUserId(request);
        return ResponseEntity.ok(ApiResponse.success(notificationPreferenceService.list(userId)));
    }

    @PatchMapping
    public ResponseEntity<ApiResponse<NotificationPreferenceResponse>> update(
            @Parameter(hidden = true) HttpServletRequest request,
            @Valid @RequestBody UpdateNotificationPreferenceRequest updateRequest
    ) {
        UUID userId = AuthRequestAttributes.getRequiredUserId(request);
        return ResponseEntity.ok(ApiResponse.success(notificationPreferenceService.update(userId, updateRequest)));
    }
}
