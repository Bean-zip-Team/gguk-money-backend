package com.ggukmoney.beanzip.domain.keycap.controller;

import com.ggukmoney.beanzip.domain.keycap.dto.request.KeycapBoxOpenRequest;
import com.ggukmoney.beanzip.domain.keycap.dto.response.KeycapBoxHistoryResponse;
import com.ggukmoney.beanzip.domain.keycap.dto.response.KeycapBoxBulkOpenResponse;
import com.ggukmoney.beanzip.domain.keycap.dto.response.KeycapBoxOpenResponse;
import com.ggukmoney.beanzip.domain.keycap.dto.response.KeycapBoxStatusResponse;
import com.ggukmoney.beanzip.domain.keycap.service.KeycapBoxBulkOpenService;
import com.ggukmoney.beanzip.domain.keycap.service.KeycapBoxOpenService;
import com.ggukmoney.beanzip.domain.keycap.service.KeycapBoxQueryService;
import com.ggukmoney.beanzip.global.common.ApiErrorResponse;
import com.ggukmoney.beanzip.global.common.ApiResponse;
import com.ggukmoney.beanzip.global.config.OpenApiConfig;
import com.ggukmoney.beanzip.global.interceptor.AuthRequestAttributes;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
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
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/keycap-boxes")
@Tag(name = "Keycap Boxes", description = "키캡 상자 상태, 개봉, 이력 API")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
public class KeycapBoxController {

    private final KeycapBoxQueryService keycapBoxQueryService;
    private final KeycapBoxOpenService keycapBoxOpenService;
    private final KeycapBoxBulkOpenService keycapBoxBulkOpenService;

    @Operation(summary = "키캡 조각 상태 조회", description = "경제 전환 전에는 상자 잔액·개봉 가능 여부를, 전환 후에는 가용 조각·뽑기 가격·진행도를 조회해요. 조회는 조각 합산이나 지급을 실행하지 않아요.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "503", description = "경제 정책을 확인할 수 없어요. 잔액과 수집 진행도는 변경하지 않아요.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "조회에 성공했어요.",
                    content = @Content(examples = @ExampleObject(
                            name = "조각 12개 보유",
                            value = """
                                    {
                                      "success": true,
                                      "data": {
                                        "shardBalance": 12,
                                        "drawPrice": 5,
                                        "canDraw": true,
                                        "shardProgressTapCount": 45,
                                        "nextShardRequiredTapCount": 100,
                                        "boxBalance": 0,
                                        "canFreeOpen": false,
                                        "canAdOpen": false,
                                        "charging": false,
                                        "nextRechargeAt": null,
                                        "boxProgressTapCount": 45,
                                        "nextBoxRequiredTapCount": 100
                                      }
                                    }
                                    """
                    ))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증에 실패했어요. 액세스 토큰이 없거나 유효하지 않아요.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "조각 지갑이 없어요.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @GetMapping("/status")
    public ResponseEntity<ApiResponse<KeycapBoxStatusResponse>> getStatus(@Parameter(hidden = true) HttpServletRequest httpServletRequest) {
        return ResponseEntity.ok(ApiResponse.success(keycapBoxQueryService.getStatus(
                AuthRequestAttributes.getRequiredUserId(httpServletRequest)
        )));
    }

    @Operation(summary = "키캡 상자 개봉", deprecated = true, description = "경제 전환 전에는 기존 무료·광고 개봉을 처리해요. 전환 후 신규 개봉은 409 KEYCAP_BOX_OPEN_RETIRED이고 과거 멱등 재응답은 유지해요.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "503", description = "경제 정책을 확인할 수 없어요. 잔액과 수집 진행도는 변경하지 않아요.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "개봉 또는 멱등 재응답에 성공했어요.", content = @Content(schema = @Schema(implementation = KeycapBoxOpenResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "멱등키 누락, 개봉 불가, 요청 값 오류", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 오류", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "상자 계정 없음", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "429", description = "공통 충전 주기 개봉 한도 초과", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "경제 전환으로 신규 개봉이 종료됐거나 멱등키를 다른 요청에 사용했거나 지급 가능한 보상이 없어요.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @PostMapping("/open")
    public ResponseEntity<ApiResponse<KeycapBoxOpenResponse>> open(
            @Parameter(in = ParameterIn.HEADER, description = "개봉 요청 멱등키. 최대 100자이며 같은 요청 재시도에 같은 값을 사용합니다.", required = true, example = "open-20260715-0001")
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody KeycapBoxOpenRequest request,
            @Parameter(hidden = true) HttpServletRequest httpServletRequest
    ) {
        return ResponseEntity.ok(ApiResponse.success(keycapBoxOpenService.open(
                AuthRequestAttributes.getRequiredUserId(httpServletRequest),
                idempotencyKey,
                request
        )));
    }

    @Operation(summary = "키캡 상자 일괄 개봉", deprecated = true,
            description = "경제 전환 전에는 알림 동의 보상으로 쌓인 상자를 한 번에 열어요. 전환 후 신규 수령은 409 KEYCAP_BOX_OPEN_RETIRED이고 기존 수령 결과는 다시 조회할 수 있어요. 개봉 주기·무료/광고 횟수 제한을 적용하지 않으며, "
                    + "유저당 1회만 가능해요. 상한은 `keycapBox.bulkOpen.limit` 설정값이고 보유량이 그보다 적으면 보유량만큼 열어요. "
                    + "보유 상자가 없으면 openedCount 0 으로 응답하며 수령 기록을 남기지 않아요.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "503", description = "경제 정책을 확인할 수 없어요. 잔액과 수집 진행도는 변경하지 않아요.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "일괄 개봉 또는 기존 수령 결과 재응답에 성공했어요.", content = @Content(schema = @Schema(implementation = KeycapBoxBulkOpenResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "멱등키 누락", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 오류", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "경제 전환 후 신규 수령은 종료됐어요.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "알림 동의 기록이 없어요.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "상자 계정 없음", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @PostMapping("/bulk-open")
    public ResponseEntity<ApiResponse<KeycapBoxBulkOpenResponse>> bulkOpen(
            @Parameter(in = ParameterIn.HEADER, description = "개봉 요청 멱등키예요. 재시도에는 같은 값을 사용해요.", required = true, example = "bulk-20260908-0001")
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Parameter(hidden = true) HttpServletRequest httpServletRequest
    ) {
        return ResponseEntity.ok(ApiResponse.success(keycapBoxBulkOpenService.bulkOpen(
                AuthRequestAttributes.getRequiredUserId(httpServletRequest),
                idempotencyKey
        )));
    }

    @Operation(summary = "키캡 상자 개봉 이력 조회", description = "커서 기반으로 키캡 상자 개봉 이력을 조회합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "잘못된 cursor 또는 size", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 오류", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @GetMapping("/history")
    public ResponseEntity<ApiResponse<KeycapBoxHistoryResponse>> getHistory(
            @Parameter(description = "다음 페이지 커서", example = "MjAyNi0wNy0xNVQwMTowMDowMFo6MTIz")
            @RequestParam(required = false) String cursor,
            @Parameter(description = "페이지 크기. 허용 범위를 벗어나면 400을 반환합니다.", example = "20")
            @RequestParam(required = false) Integer size,
            @Parameter(hidden = true) HttpServletRequest httpServletRequest
    ) {
        return ResponseEntity.ok(ApiResponse.success(keycapBoxQueryService.getHistory(
                AuthRequestAttributes.getRequiredUserId(httpServletRequest),
                cursor,
                size
        )));
    }
}
