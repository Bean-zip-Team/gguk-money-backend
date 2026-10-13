package com.ggukmoney.beanzip.domain.keycap.controller;

import com.ggukmoney.beanzip.domain.keycap.dto.response.KeycapBoxHistoryResponse;
import com.ggukmoney.beanzip.domain.keycap.dto.response.KeycapBoxStatusResponse;
import com.ggukmoney.beanzip.domain.keycap.service.KeycapBoxQueryService;
import com.ggukmoney.beanzip.global.common.ApiErrorResponse;
import com.ggukmoney.beanzip.global.common.ApiResponse;
import com.ggukmoney.beanzip.global.config.OpenApiConfig;
import com.ggukmoney.beanzip.global.interceptor.AuthRequestAttributes;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 조각 상태와 상자 개봉 이력 (BEA-329). 개봉·일괄 개봉은 사라졌고 뽑기는 {@code /api/keycaps/draw} 다.
 * 경로는 구버전 앱 호환을 위해 그대로 둔다.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/keycap-boxes")
@Tag(name = "Keycap Boxes", description = "키캡 조각 상태와 상자 개봉 이력 API예요.")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
public class KeycapBoxController {

    private final KeycapBoxQueryService keycapBoxQueryService;

    @Operation(summary = "키캡 조각 상태 조회", description = "조각 잔액, 뽑기 가격, 다음 조각까지의 진행도를 조회해요. 상자 시절 필드는 구버전 앱 호환을 위해 중립값으로 남아 있어요.")
    @ApiResponses({
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

    @Operation(summary = "키캡 상자 개봉 이력 조회", description = "커서 기반으로 과거 상자 개봉 이력을 최근 개봉 순으로 조회해요. 상자 개봉은 더 이상 일어나지 않아요.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회에 성공했어요."),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "`cursor` 값이 잘못됐거나 `size` 값이 허용 범위를 벗어났어요.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증에 실패했어요. 액세스 토큰이 없거나 유효하지 않아요.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @GetMapping("/history")
    public ResponseEntity<ApiResponse<KeycapBoxHistoryResponse>> getHistory(
            @Parameter(description = "다음 페이지 커서예요. 이전 응답의 `nextCursor` 값을 넣고, 첫 페이지는 비워 두세요.", example = "MjAyNi0wNy0xNVQwMTowMDowMFo6MTIz")
            @RequestParam(required = false) String cursor,
            @Parameter(description = "페이지 크기예요. 1부터 100까지 받고 기본값은 20이에요. 허용 범위를 벗어나면 400을 돌려줘요.", example = "20")
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
