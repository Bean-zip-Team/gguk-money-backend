package com.ggukmoney.beanzip.domain.friend.controller;

import com.ggukmoney.beanzip.domain.friend.dto.request.CheerSendRequest;
import com.ggukmoney.beanzip.domain.friend.dto.response.CheerSendResponse;
import com.ggukmoney.beanzip.domain.friend.service.CheerService;
import com.ggukmoney.beanzip.domain.friend.service.FriendAccess;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/cheers")
@Tag(name = "Cheers", description = "친구에게 응원을 보내고 받은 응원을 확인하는 API예요.")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
public class CheerController {

    private final FriendAccess friendAccess;
    private final CheerService cheerService;

    @Operation(summary = "응원 보내기", description = "친구 한 명에게 응원을 보내요. 하루에 `friendPolicy.cheerDailyLimit` 명까지, 같은 친구에게는 하루 한 번 보낼 수 있어요. 하루는 한국 시간 기준이에요. 오늘 첫 응원이면 보낸 사람도 조각을 받아요. `Idempotency-Key` 는 받지 않아요. 다시 보낸 요청이 `CHEER_ALREADY_SENT_TODAY` 로 오면 이미 보낸 것으로 처리하면 돼요.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "응원을 보냈어요."),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "요청 값이 올바르지 않아요. `toUserId` 가 없거나 UUID 형식이 아니에요.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증에 실패했어요. 액세스 토큰이 없거나 유효하지 않아요.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "`NOT_FRIENDS`: 친구가 아니거나 탈퇴한 사용자예요. `ACCOUNT_SUSPENDED`: 보낸 사람이 정지된 계정이에요.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "`CHEER_ALREADY_SENT_TODAY`: 오늘 이 친구에게 이미 응원했어요. `FRIENDS_DISABLED`: 친구 기능이 꺼져 있어요.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "429", description = "`CHEER_DAILY_LIMIT_EXCEEDED`: 오늘 응원할 수 있는 수를 다 썼어요.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @PostMapping
    public ResponseEntity<ApiResponse<CheerSendResponse>> send(
            @Valid @RequestBody CheerSendRequest request,
            @Parameter(hidden = true) HttpServletRequest httpServletRequest
    ) {
        var userId = AuthRequestAttributes.getRequiredUserId(httpServletRequest);
        friendAccess.requireEnabled();
        return ResponseEntity.ok(ApiResponse.success(cheerService.send(userId, request.toUserId())));
    }
}
