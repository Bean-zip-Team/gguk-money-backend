package com.ggukmoney.beanzip.domain.auth.controller;

import com.ggukmoney.beanzip.domain.auth.dto.request.LogoutRequest;
import com.ggukmoney.beanzip.domain.auth.dto.request.RefreshTokenRequest;
import com.ggukmoney.beanzip.domain.auth.dto.request.TossLoginRequest;
import com.ggukmoney.beanzip.domain.auth.dto.request.TossUnlinkWebhookRequest;
import com.ggukmoney.beanzip.domain.auth.dto.response.AuthTokenResponse;
import com.ggukmoney.beanzip.domain.auth.dto.response.LogoutAllResponse;
import com.ggukmoney.beanzip.domain.auth.dto.response.LogoutResponse;
import com.ggukmoney.beanzip.domain.auth.dto.response.TossUnlinkWebhookResponse;
import com.ggukmoney.beanzip.domain.auth.service.AuthService;
import com.ggukmoney.beanzip.global.common.ApiPaths;
import com.ggukmoney.beanzip.global.common.ApiResponse;
import com.ggukmoney.beanzip.global.interceptor.AuthRequestAttributes;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping(ApiPaths.AUTH)
public class AuthController {

    private static final String AUTHORIZATION_HEADER = "Authorization";

    private final AuthService authService;

    @PostMapping("/toss/login")
    public ResponseEntity<ApiResponse<AuthTokenResponse>> loginWithToss(
            @Valid @RequestBody TossLoginRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(authService.loginWithToss(request)));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<AuthTokenResponse>> refresh(
            @Valid @RequestBody RefreshTokenRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(authService.refresh(request)));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<LogoutResponse>> logout(
            @RequestBody(required = false) LogoutRequest request,
            HttpServletRequest httpServletRequest
    ) {
        return ResponseEntity.ok(ApiResponse.success(authService.logoutCurrentSession(
                AuthRequestAttributes.getRequiredUserId(httpServletRequest),
                AuthRequestAttributes.getRequiredSessionId(httpServletRequest),
                AuthRequestAttributes.getOptionalString(httpServletRequest, AuthRequestAttributes.ACCESS_TOKEN_JTI),
                AuthRequestAttributes.getOptionalInstant(httpServletRequest, AuthRequestAttributes.ACCESS_TOKEN_EXPIRES_AT),
                request == null ? null : request.refreshToken()
        )));
    }

    @PostMapping("/logout-all")
    public ResponseEntity<ApiResponse<LogoutAllResponse>> logoutAll(HttpServletRequest request) {
        return ResponseEntity.ok(ApiResponse.success(authService.logoutAll(
                AuthRequestAttributes.getRequiredUserId(request),
                AuthRequestAttributes.getOptionalString(request, AuthRequestAttributes.ACCESS_TOKEN_JTI),
                AuthRequestAttributes.getOptionalInstant(request, AuthRequestAttributes.ACCESS_TOKEN_EXPIRES_AT)
        )));
    }

    @PostMapping("/toss/unlink-webhook")
    public ResponseEntity<ApiResponse<TossUnlinkWebhookResponse>> handleTossUnlinkWebhook(
            @RequestHeader(AUTHORIZATION_HEADER) String authorization,
            @Valid @RequestBody TossUnlinkWebhookRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(authService.handleTossUnlinkWebhook(authorization, request)));
    }
}
