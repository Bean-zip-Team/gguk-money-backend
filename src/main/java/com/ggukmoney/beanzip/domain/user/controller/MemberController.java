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
@RequestMapping("/api/members/me")
@Tag(name = "Members", description = "내 회원 정보를 조회, 수정하고 탈퇴하는 API예요.")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
public class MemberController {

    private final AuthService authService;
    private final UserService userService;

    @Operation(summary = "내 회원 정보 조회", description = "내 회원 정보와 장착한 키캡, 포인트 잔액을 함께 조회해요. 닉네임은 마스킹해서 돌려줘요.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회에 성공했어요."),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증에 실패했어요. 액세스 토큰이 없거나 유효하지 않아요.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "탈퇴한 계정이라 조회할 수 없어요.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @GetMapping
    public ResponseEntity<ApiResponse<MemberMeResponse>> getMe(@Parameter(hidden = true) HttpServletRequest httpServletRequest) {
        return ResponseEntity.ok(ApiResponse.success(userService.getCurrentMember(
                AuthRequestAttributes.getRequiredUserId(httpServletRequest)
        )));
    }

    @Operation(summary = "내 회원 정보 수정", description = "닉네임과 프로필 이미지 URL 중 하나 이상을 수정해요. 보내지 않은 항목은 그대로 둬요.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "수정에 성공했어요."),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "요청 값이 올바르지 않아요. 수정할 항목이 하나도 없거나, 닉네임이 공백뿐이거나, 길이 제한을 넘으면 이 응답을 받아요.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증에 실패했어요. 액세스 토큰이 없거나 유효하지 않아요.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "다른 활성 회원이 이미 같은 닉네임을 쓰고 있어요. 대소문자는 구분하지 않아요.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
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

    @Operation(summary = "회원 탈퇴", description = "Toss 인가 코드로 본인을 확인하고 Toss 연결을 해제한 뒤 현재 사용자를 탈퇴 처리해요. 탈퇴하면 모든 세션이 끊겨요. 이미 탈퇴한 사용자가 다시 요청하면 Toss를 호출하지 않고 성공으로 응답해요.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "탈퇴에 성공했어요."),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "요청 값이 올바르지 않아요. `authorizationCode` 필드가 비어 있으면 이 응답을 받아요.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증에 실패했어요. 액세스 토큰이 없거나 유효하지 않아요. Toss 인가 코드가 유효하지 않아도 이 응답을 받아요.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "502", description = "Toss API 호출이 실패했거나 Toss 응답이 올바르지 않아요.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
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
