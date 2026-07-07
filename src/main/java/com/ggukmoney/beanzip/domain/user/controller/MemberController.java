package com.ggukmoney.beanzip.domain.user.controller;

import com.ggukmoney.beanzip.domain.auth.service.AuthService;
import com.ggukmoney.beanzip.domain.user.dto.request.UserWithdrawalRequest;
import com.ggukmoney.beanzip.domain.user.dto.response.UserWithdrawalResponse;
import com.ggukmoney.beanzip.global.common.ApiResponse;
import com.ggukmoney.beanzip.global.interceptor.AuthRequestAttributes;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/members/me")
public class MemberController {

    private final AuthService authService;

    @PostMapping("/withdrawal")
    public ResponseEntity<ApiResponse<UserWithdrawalResponse>> withdraw(
            @Valid @RequestBody UserWithdrawalRequest request,
            HttpServletRequest httpServletRequest
    ) {
        return ResponseEntity.ok(ApiResponse.success(authService.withdrawCurrentUser(
                AuthRequestAttributes.getRequiredUserId(httpServletRequest),
                AuthRequestAttributes.getOptionalString(httpServletRequest, AuthRequestAttributes.ACCESS_TOKEN_JTI),
                AuthRequestAttributes.getOptionalInstant(httpServletRequest, AuthRequestAttributes.ACCESS_TOKEN_EXPIRES_AT),
                request
        )));
    }
}
