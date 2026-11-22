package com.ggukmoney.beanzip.domain.friend.controller;

import com.ggukmoney.beanzip.domain.friend.dto.request.InviteAcceptRequest;
import com.ggukmoney.beanzip.domain.friend.dto.request.InviteAckRequest;
import com.ggukmoney.beanzip.domain.friend.dto.request.InviteVisitRequest;
import com.ggukmoney.beanzip.domain.friend.dto.response.InviteAcceptResponse;
import com.ggukmoney.beanzip.domain.friend.dto.response.InviteAckResponse;
import com.ggukmoney.beanzip.domain.friend.dto.response.InviteCodeResponse;
import com.ggukmoney.beanzip.domain.friend.dto.response.InviteSuccessListResponse;
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

    @Operation(summary = "초대 수락", description = "이미 로그인된 회원이 초대 링크로 들어왔을 때 불러요. 보상은 없고 초대한 사람과 친구가 돼요. 새로 친구가 됐으면 초대한 사람에게 '친구 연결' 푸시가 가요. 앱은 성공하거나 `INVITE_CODE_NOT_FOUND`, `INVITE_SELF` 를 받으면 보관한 초대 코드를 지워요. `FRIENDS_DISABLED` 나 통신 오류면 지우지 않고 다음 진입 때 다시 불러요.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "친구가 됐거나 이미 친구였어요."),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "`INVITE_SELF`: 내 초대 코드예요. 요청 값이 비어 있어도 400이에요.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증에 실패했어요. 액세스 토큰이 없거나 유효하지 않아요.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "`INVITE_CODE_NOT_FOUND`: 없는 코드이거나 초대한 사람이 탈퇴했어요.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "친구 기능이 꺼져 있어요. `FRIENDS_DISABLED`", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
    @PostMapping("/accept")
    public ResponseEntity<ApiResponse<InviteAcceptResponse>> accept(
            @Valid @RequestBody InviteAcceptRequest request,
            @Parameter(hidden = true) HttpServletRequest httpServletRequest
    ) {
        var userId = AuthRequestAttributes.getRequiredUserId(httpServletRequest);
        friendAccess.requireEnabled();
        return ResponseEntity.ok(ApiResponse.success(inviteService.accept(userId, request.inviteCode())));
    }

    @Operation(summary = "확인하지 않은 초대 성공 조회", description = "내 초대로 가입했고 내가 아직 확인하지 않은 친구 목록이에요. 결과가 있으면 모달을 띄우기 직전에 `POST /api/invites/success/ack` 를 부르고, 응답을 받은 뒤 모달을 띄워요. 이미 회원이던 사람과 친구가 된 경우와 탈퇴한 사용자의 건은 나오지 않아요.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회에 성공했어요. 없으면 빈 목록이에요."),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증에 실패했어요. 액세스 토큰이 없거나 유효하지 않아요.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "친구 기능이 꺼져 있어요. `FRIENDS_DISABLED`", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
    @GetMapping("/success/unread")
    public ResponseEntity<ApiResponse<InviteSuccessListResponse>> unreadSuccesses(
            @Parameter(hidden = true) HttpServletRequest httpServletRequest
    ) {
        var userId = AuthRequestAttributes.getRequiredUserId(httpServletRequest);
        friendAccess.requireEnabled();
        return ResponseEntity.ok(ApiResponse.success(inviteService.unreadSuccesses(userId)));
    }

    @Operation(summary = "초대 성공 확인", description = "초대 성공을 확인하고 한 건에 `shardsPerInvite` 개씩 조각을 받아요. 모달을 띄우기 직전에 부르고, 모달에는 이 응답의 결과를 보여 줘요. 이미 확인했거나, 내 것이 아니거나, 초대받은 사람이 탈퇴한 건은 건너뛰어요. 같은 요청을 다시 보내도 한 번만 지급해요.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "확인했어요. 건너뛴 건만 있으면 `shardsGranted` 가 0이에요."),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "요청 값이 올바르지 않아요. `inviteIds` 가 없거나, 100개를 넘거나, UUID 형식이 아닌 값이 있어요.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증에 실패했어요. 액세스 토큰이 없거나 유효하지 않아요.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "친구 기능이 꺼져 있어요. `FRIENDS_DISABLED`", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
    @PostMapping("/success/ack")
    public ResponseEntity<ApiResponse<InviteAckResponse>> ackSuccesses(
            @Valid @RequestBody InviteAckRequest request,
            @Parameter(hidden = true) HttpServletRequest httpServletRequest
    ) {
        var userId = AuthRequestAttributes.getRequiredUserId(httpServletRequest);
        friendAccess.requireEnabled();
        return ResponseEntity.ok(ApiResponse.success(inviteService.ackSuccesses(userId, request.inviteIds())));
    }
}
