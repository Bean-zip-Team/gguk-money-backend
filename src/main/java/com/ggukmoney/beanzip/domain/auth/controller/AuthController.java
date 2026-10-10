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
import com.ggukmoney.beanzip.global.common.ApiErrorResponse;
import com.ggukmoney.beanzip.global.common.ApiResponse;
import com.ggukmoney.beanzip.global.config.OpenApiConfig;
import com.ggukmoney.beanzip.global.interceptor.AuthRequestAttributes;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@RequestMapping("/api/auth")
@Tag(name = "Auth", description = "로그인, 토큰 갱신, 로그아웃처럼 인증과 세션을 다루는 API예요.")
public class AuthController {

    private static final String AUTHORIZATION_HEADER = "Authorization";

    private final AuthService authService;

    @Operation(summary = "Toss 로그인", description = "Toss 인가 코드로 로그인하고 액세스 토큰과 리프레시 토큰을 발급해요. 처음 보는 Toss 계정이면 회원을 새로 만들어요. 탈퇴한 회원이 다시 로그인하면 계정을 다시 활성화해요.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "로그인에 성공했어요."),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "요청 값이 올바르지 않아요. `authorizationCode` 필드가 비어 있으면 이 응답을 받아요.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Toss 인가 코드가 유효하지 않아요.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "502", description = "Toss API 호출이 실패했거나 Toss 응답이 올바르지 않아요.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @PostMapping("/toss/login")
    public ResponseEntity<ApiResponse<AuthTokenResponse>> loginWithToss(
            @Valid @RequestBody TossLoginRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(authService.loginWithToss(request)));
    }

    @Operation(summary = "토큰 갱신", description = "리프레시 토큰으로 새 액세스 토큰과 리프레시 토큰을 발급해요. 보낸 리프레시 토큰은 교체되니 응답으로 받은 새 토큰을 저장해야 해요.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "토큰 갱신에 성공했어요."),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "요청 값이 올바르지 않아요. `refreshToken` 필드가 비어 있으면 이 응답을 받아요.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "리프레시 토큰이 유효하지 않아요. 토큰이 만료됐거나 세션이 없으면 이 응답을 받아요. 이미 교체된 토큰을 2초가 지난 뒤 다시 보내면 세션도 끊겨요.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "토큰 갱신이 충돌했어요. 같은 리프레시 토큰으로 2초 안에 다시 갱신을 요청하면 이 응답을 받아요.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<AuthTokenResponse>> refresh(
            @Valid @RequestBody RefreshTokenRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(authService.refresh(request)));
    }

    @Operation(summary = "현재 세션 로그아웃", description = "현재 액세스 토큰의 세션을 로그아웃해요. 이 세션의 액세스 토큰과 리프레시 토큰을 더는 쓸 수 없어요.", security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "로그아웃에 성공했어요."),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "요청 값이 올바르지 않아요. 요청 본문 형식이 잘못되면 이 응답을 받아요.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증에 실패했어요. 액세스 토큰이 없거나 유효하지 않아요. 본문의 리프레시 토큰이 현재 세션과 맞지 않아도 이 응답을 받아요.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<LogoutResponse>> logout(
            @RequestBody(required = false) LogoutRequest request,
            @Parameter(hidden = true) HttpServletRequest httpServletRequest
    ) {
        return ResponseEntity.ok(ApiResponse.success(authService.logoutCurrentSession(
                AuthRequestAttributes.getRequiredUserId(httpServletRequest),
                AuthRequestAttributes.getRequiredSessionId(httpServletRequest),
                AuthRequestAttributes.getOptionalString(httpServletRequest, AuthRequestAttributes.ACCESS_TOKEN_JTI),
                AuthRequestAttributes.getOptionalInstant(httpServletRequest, AuthRequestAttributes.ACCESS_TOKEN_EXPIRES_AT),
                request == null ? null : request.refreshToken()
        )));
    }

    @Operation(summary = "전체 세션 로그아웃", description = "현재 사용자에게 연결된 모든 인증 세션을 로그아웃해요.", security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "전체 로그아웃에 성공했어요."),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증에 실패했어요. 액세스 토큰이 없거나 유효하지 않아요.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "503", description = "인증 저장소(Redis)에 접근하지 못해 세션을 끊지 못했어요.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @PostMapping("/logout-all")
    public ResponseEntity<ApiResponse<LogoutAllResponse>> logoutAll(@Parameter(hidden = true) HttpServletRequest request) {
        return ResponseEntity.ok(ApiResponse.success(authService.logoutAll(
                AuthRequestAttributes.getRequiredUserId(request),
                AuthRequestAttributes.getOptionalString(request, AuthRequestAttributes.ACCESS_TOKEN_JTI),
                AuthRequestAttributes.getOptionalInstant(request, AuthRequestAttributes.ACCESS_TOKEN_EXPIRES_AT)
        )));
    }

    @Operation(summary = "Toss 연결 해제 웹훅", description = "Toss 연결 해제 웹훅을 처리해요. 액세스 토큰 대신 `Authorization` 헤더의 Basic 시크릿으로 인증해요. 연결된 회원이 있으면 탈퇴 처리하고 모든 세션을 끊어요.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "웹훅 처리에 성공했어요."),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "요청 값이 올바르지 않아요. 지원하지 않는 웹훅 이벤트이거나 필수 값이 빠지면 이 응답을 받아요.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "웹훅 인증에 실패했어요. `Authorization` 헤더가 Basic 방식이 아니거나 시크릿이 맞지 않아요.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @PostMapping("/toss/unlink-webhook")
    public ResponseEntity<ApiResponse<TossUnlinkWebhookResponse>> handleTossUnlinkWebhook(
            @Parameter(in = ParameterIn.HEADER, description = "Toss 웹훅 인증 값이에요. `Basic` 뒤에 웹훅 시크릿을 Base64로 인코딩한 값을 붙여 보내요.", required = true)
            @RequestHeader(AUTHORIZATION_HEADER) String authorization,
            @Valid @RequestBody TossUnlinkWebhookRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(authService.handleTossUnlinkWebhook(authorization, request)));
    }
}
