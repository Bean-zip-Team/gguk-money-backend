package com.ggukmoney.beanzip.domain.friend.controller;

import com.ggukmoney.beanzip.domain.friend.dto.response.InviteCodeResponse;
import com.ggukmoney.beanzip.domain.friend.service.FriendAccess;
import com.ggukmoney.beanzip.domain.friend.service.InviteCodeService;
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
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/invites")
@Tag(name = "Invites", description = "친구를 초대하고 초대 성공을 확인하는 API예요.")
public class InviteController {

    private final FriendAccess friendAccess;
    private final InviteCodeService inviteCodeService;

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
