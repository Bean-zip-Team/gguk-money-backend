package com.ggukmoney.beanzip.domain.user.controller;

import com.ggukmoney.beanzip.domain.auth.service.AuthService;
import com.ggukmoney.beanzip.domain.user.dto.request.UpdateMemberRequest;
import com.ggukmoney.beanzip.domain.user.dto.request.UserWithdrawalRequest;
import com.ggukmoney.beanzip.domain.user.dto.response.MemberMeResponse;
import com.ggukmoney.beanzip.domain.user.dto.response.MemberUpdateResponse;
import com.ggukmoney.beanzip.domain.user.dto.response.UserWithdrawalResponse;
import com.ggukmoney.beanzip.domain.user.service.UserService;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/members/me")
@Tag(name = "Members", description = "내 회원 정보 API")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
public class MemberController {

    private final AuthService authService;
    private final UserService userService;

    @Operation(summary = "내 회원 정보 조회")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 오류", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "탈퇴 계정", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @GetMapping
    public ResponseEntity<ApiResponse<MemberMeResponse>> getMe(@Parameter(hidden = true) HttpServletRequest httpServletRequest) {
        return ResponseEntity.ok(ApiResponse.success(userService.getCurrentMember(
                AuthRequestAttributes.getRequiredUserId(httpServletRequest)
        )));
    }

    @Operation(summary = "내 회원 정보 수정", description = "닉네임 또는 프로필 이미지 URL 중 하나 이상을 수정합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "수정 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "요청 값 오류", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 오류", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "닉네임 중복", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @PatchMapping
    public ResponseEntity<ApiResponse<MemberUpdateResponse>> updateMe(
            @Valid @RequestBody UpdateMemberRequest request,
            @Parameter(hidden = true) HttpServletRequest httpServletRequest
    ) {
        return ResponseEntity.ok(ApiResponse.success(userService.updateCurrentMember(
                AuthRequestAttributes.getRequiredUserId(httpServletRequest),
                request
        )));
    }

    @Operation(summary = "회원 탈퇴", description = "Toss 인가 코드로 연결 해제를 확인한 뒤 현재 사용자를 탈퇴 처리합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "탈퇴 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "요청 값 오류", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 오류", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "502", description = "Toss 연동 오류", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @PostMapping("/withdrawal")
    public ResponseEntity<ApiResponse<UserWithdrawalResponse>> withdraw(
            @Valid @RequestBody UserWithdrawalRequest request,
            @Parameter(hidden = true) HttpServletRequest httpServletRequest
    ) {
        return ResponseEntity.ok(ApiResponse.success(authService.withdrawCurrentUser(
                AuthRequestAttributes.getRequiredUserId(httpServletRequest),
                AuthRequestAttributes.getOptionalString(httpServletRequest, AuthRequestAttributes.ACCESS_TOKEN_JTI),
                AuthRequestAttributes.getOptionalInstant(httpServletRequest, AuthRequestAttributes.ACCESS_TOKEN_EXPIRES_AT),
                request
        )));
    }
}
