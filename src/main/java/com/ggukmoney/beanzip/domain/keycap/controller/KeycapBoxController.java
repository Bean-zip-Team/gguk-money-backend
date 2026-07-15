package com.ggukmoney.beanzip.domain.keycap.controller;

import com.ggukmoney.beanzip.domain.keycap.dto.request.KeycapBoxOpenRequest;
import com.ggukmoney.beanzip.domain.keycap.dto.response.KeycapBoxHistoryResponse;
import com.ggukmoney.beanzip.domain.keycap.dto.response.KeycapBoxOpenResponse;
import com.ggukmoney.beanzip.domain.keycap.dto.response.KeycapBoxStatusResponse;
import com.ggukmoney.beanzip.domain.keycap.service.KeycapBoxHistoryService;
import com.ggukmoney.beanzip.domain.keycap.service.KeycapBoxOpenService;
import com.ggukmoney.beanzip.domain.keycap.service.KeycapBoxStatusService;
import com.ggukmoney.beanzip.global.common.ApiPaths;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping(ApiPaths.KEYCAP_BOXES)
@Tag(name = "Keycap Boxes", description = "키캡 상자 상태, 개봉, 이력 API")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
public class KeycapBoxController {

    private final KeycapBoxStatusService keycapBoxStatusService;
    private final KeycapBoxOpenService keycapBoxOpenService;
    private final KeycapBoxHistoryService keycapBoxHistoryService;

    @Operation(summary = "키캡 상자 상태 조회")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 오류", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "상자 계정 또는 탭 진행도 없음", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @GetMapping("/status")
    public ResponseEntity<ApiResponse<KeycapBoxStatusResponse>> getStatus(@Parameter(hidden = true) HttpServletRequest httpServletRequest) {
        return ResponseEntity.ok(ApiResponse.success(keycapBoxStatusService.getStatus(
                AuthRequestAttributes.getRequiredUserId(httpServletRequest)
        )));
    }

    @Operation(summary = "키캡 상자 개봉", description = "보유 상자와 무료 개봉권을 사용해 키캡 조각을 지급합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "개봉 성공 또는 멱등 재응답"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "멱등키 누락, 개봉 불가, 요청 값 오류", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 오류", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "상자 계정 없음", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "멱등키 재사용 또는 보상 없음", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @PostMapping("/open")
    public ResponseEntity<ApiResponse<KeycapBoxOpenResponse>> open(
            @Parameter(in = ParameterIn.HEADER, description = "개봉 요청 멱등키. 최대 128자이며 같은 요청 재시도에 같은 값을 사용합니다.", required = true, example = "open-20260715-0001")
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
        return ResponseEntity.ok(ApiResponse.success(keycapBoxHistoryService.getHistory(
                AuthRequestAttributes.getRequiredUserId(httpServletRequest),
                cursor,
                size
        )));
    }
}
