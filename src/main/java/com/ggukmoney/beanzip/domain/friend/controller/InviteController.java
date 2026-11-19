package com.ggukmoney.beanzip.domain.friend.controller;

import com.ggukmoney.beanzip.domain.friend.dto.request.InviteVisitRequest;
import com.ggukmoney.beanzip.domain.friend.dto.response.InviteCodeResponse;
import com.ggukmoney.beanzip.domain.friend.service.FriendAccess;
import com.ggukmoney.beanzip.domain.friend.service.InviteCodeService;
import com.ggukmoney.beanzip.domain.friend.service.InviteService;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/invites")
@Tag(name = "Invites", description = "친구를 초대하고 초대 성공을 확인하는 API예요.")
public class InviteController {

    private final FriendAccess friendAccess;
    private final InviteCodeService inviteCodeService;
    private final InviteService inviteService;

    @Operation(summary = "초대 링크 방문 기록", description = "앱이 초대 링크로 열렸고 로그인되어 있지 않을 때 불러요. 로그인 없이 부를 수 있어요. 응답을 기다리지 않아도 되고, 실패해도 앱 시작을 막지 않아요. 로그인되어 있으면 이 API 대신 `POST /api/invites/accept` 를 불러요.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204", description = "받았어요. 없는 코드, 탈퇴한 초대자, 꺼진 친구 기능, 같은 `visitKey` 재호출도 204 이고 이때는 아무것도 기록하지 않아요."),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "요청 값이 올바르지 않아요. 값이 빠졌거나 형식이 틀린 경우예요.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @PostMapping("/visits")
    public ResponseEntity<Void> recordVisit(@Valid @RequestBody InviteVisitRequest request) {
        inviteService.recordVisit(request.inviteCode(), request.visitKey());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "내 초대 코드 조회", description = "내 초대 코드를 조회해요. 아직 없으면 발급해요. 코드는 바뀌지 않아요.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회에 성공했어요."),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증에 실패했어요. 액세스 토큰이 없거나 유효하지 않아요.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "친구 기능이 꺼져 있어요. `FRIENDS_DISABLED`", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<InviteCodeResponse>> myCode(@Parameter(hidden = true) HttpServletRequest httpServletRequest) {
        var userId = AuthRequestAttributes.getRequiredUserId(httpServletRequest);
        friendAccess.requireEnabled();
        return ResponseEntity.ok(ApiResponse.success(new InviteCodeResponse(inviteCodeService.getOrCreate(userId))));
    }
}
