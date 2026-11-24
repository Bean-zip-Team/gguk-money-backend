package com.ggukmoney.beanzip.domain.friend.controller;

import com.ggukmoney.beanzip.domain.friend.dto.response.FriendListResponse;
import com.ggukmoney.beanzip.domain.friend.dto.response.FriendSummaryResponse;
import com.ggukmoney.beanzip.domain.friend.service.FriendAccess;
import com.ggukmoney.beanzip.domain.friend.service.FriendQueryService;
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
@RequestMapping("/api/friends")
@Tag(name = "Friends", description = "친구 목록과 홈 요약을 조회하는 API예요.")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
public class FriendController {

    private final FriendAccess friendAccess;
    private final FriendQueryService friendQueryService;

    @Operation(summary = "친구 목록 조회", description = "친구들과 오늘 남은 응원 수를 조회해요. \"오늘\"은 한국 시간 기준이에요.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회에 성공했어요. 친구가 없으면 빈 목록이에요."),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증에 실패했어요. 액세스 토큰이 없거나 유효하지 않아요.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "친구 기능이 꺼져 있어요. `FRIENDS_DISABLED`", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @GetMapping
    public ResponseEntity<ApiResponse<FriendListResponse>> list(@Parameter(hidden = true) HttpServletRequest httpServletRequest) {
        var userId = AuthRequestAttributes.getRequiredUserId(httpServletRequest);
        friendAccess.requireEnabled();
        return ResponseEntity.ok(ApiResponse.success(friendQueryService.list(userId)));
    }

    @Operation(summary = "친구 요약 조회", description = "홈 화면의 얼굴 스트립과 배지에 쓰는 값이에요.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회에 성공했어요."),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증에 실패했어요. 액세스 토큰이 없거나 유효하지 않아요.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "친구 기능이 꺼져 있어요. `FRIENDS_DISABLED`", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<FriendSummaryResponse>> summary(@Parameter(hidden = true) HttpServletRequest httpServletRequest) {
        var userId = AuthRequestAttributes.getRequiredUserId(httpServletRequest);
        friendAccess.requireEnabled();
        return ResponseEntity.ok(ApiResponse.success(friendQueryService.summary(userId)));
    }
}
