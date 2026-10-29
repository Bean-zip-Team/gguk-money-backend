package com.ggukmoney.beanzip.domain.tap.controller;

import com.ggukmoney.beanzip.domain.tap.dto.request.TapBatchSubmitRequest;
import com.ggukmoney.beanzip.domain.tap.dto.response.TapBatchSubmitResponse;
import com.ggukmoney.beanzip.domain.tap.dto.response.TapTodayStatusResponse;
import com.ggukmoney.beanzip.domain.tap.service.TapBatchService;
import com.ggukmoney.beanzip.domain.tap.service.TapStatusService;
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
@RequestMapping("/api/tap")
@Tag(name = "Tap", description = "탭을 적립하고 오늘 탭 상태를 조회하는 API예요.")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
public class TapController {

    private final TapBatchService tapBatchService;
    private final TapStatusService tapStatusService;

    @Operation(summary = "탭 배치 제출", description = "클라이언트가 모아 둔 탭 수를 배치로 제출해요. 미수령 자동 클릭을 먼저 정산하고 직접 탭에 현재 장착 효과를 적용해요. 같은 `tapSessionId` 필드와 `sequence` 필드 조합을 다시 보내면 추가 적립이나 자동 클릭 정산 없이 처음 인정된 탭 수와 현재 상태를 돌려줘요. 이때 `pointsAwarded`, `shardsDropped`, `effectiveCount`, `autoClicksGranted`는 0이에요. 자동 클릭과 직접 탭은 각각 한 배치로 처리하며 기본 포인트 지급 기회는 배치마다 한 번이에요. 포인트는 일일 탭 상한(`tap.validity.maxPerDay`)과 포인트 상한을 적용해요. 상한 이후 희박 지급(`tap.point.overflow.enabled`)이 켜져 있으면 상한에 닿은 뒤의 직접 탭마다 낮은 확률로 1P 가 더 나올 수 있고, 이때 하루 총 상한은 포인트 상한에 `tap.point.overflow.dailyCap`을 더한 값이에요.조각과 랭킹은 클릭 크리티컬(②)과 자동 클릭(⑤)을 포함한 클릭 수로 쌓여요. 미션은 ②⑤와 보상 한도의 영향을 받지 않고 직접 제출한 탭 수로 진행돼요.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "제출에 성공했어요."),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "요청 값이 올바르지 않아요. 필수 필드가 빠졌거나 `sequence`, `submittedCount` 필드가 허용 범위를 벗어난 경우예요.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증에 실패했어요. 액세스 토큰이 없거나 유효하지 않아요.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "사용자나 정산에 필요한 포인트 계정, 조각 지갑, 탭 진행 정보가 없어요.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "429", description = "탭 제출 속도 제한에 걸렸어요. 속도 제한(`tap.rateLimit.enabled`)이 켜져 있을 때 짧은 시간에 너무 자주 제출하면 이 응답을 받아요.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "503", description = "Redis 오류(TAP_REDIS_UNAVAILABLE) 또는 패시브 정책 조회 실패(PASSIVE_POLICY_UNAVAILABLE), 요청당 한 번 읽는 경제 정책 조회 실패(KEYCAP_ECONOMY_POLICY_UNAVAILABLE)예요. 정책 조회 실패 시 탭 지급과 저장은 롤백돼요.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @PostMapping("/batches")
    public ResponseEntity<ApiResponse<TapBatchSubmitResponse>> submitBatch(
            @Valid @RequestBody TapBatchSubmitRequest request,
            @Parameter(hidden = true) HttpServletRequest httpServletRequest
    ) {
        var userId = AuthRequestAttributes.getRequiredUserId(httpServletRequest);
        return ResponseEntity.ok(ApiResponse.success(tapBatchService.submitBatch(userId, request)));
    }

    @Operation(summary = "오늘 탭 상태 조회", description = "오늘 친 탭 수와 포인트, 키캡 조각 진행 상태를 조회해요. 날짜는 서비스 기준 시간대(기본 Asia/Seoul)로 정해요. 유휴 시간이 지나 만료된 조각 세션은 리셋된 값으로 보여줘요.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회에 성공했어요."),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증에 실패했어요. 액세스 토큰이 없거나 유효하지 않아요.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "탭 진행도가 없어요. 가입할 때 만들어지는 진행도 행이 없는 경우예요.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @GetMapping("/today")
    public ResponseEntity<ApiResponse<TapTodayStatusResponse>> today(@Parameter(hidden = true) HttpServletRequest httpServletRequest) {
        var userId = AuthRequestAttributes.getRequiredUserId(httpServletRequest);
        return ResponseEntity.ok(ApiResponse.success(tapStatusService.getTodayStatus(userId)));
    }
}
