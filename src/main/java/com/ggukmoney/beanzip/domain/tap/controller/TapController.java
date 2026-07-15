package com.ggukmoney.beanzip.domain.tap.controller;

import com.ggukmoney.beanzip.domain.tap.dto.request.TapBatchSubmitRequest;
import com.ggukmoney.beanzip.domain.tap.dto.response.TapBatchSubmitResponse;
import com.ggukmoney.beanzip.domain.tap.dto.response.TapTodayStatusResponse;
import com.ggukmoney.beanzip.domain.tap.service.TapBatchService;
import com.ggukmoney.beanzip.domain.tap.service.TapStatusService;
import com.ggukmoney.beanzip.global.common.ApiPaths;
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
@RequestMapping(ApiPaths.TAP)
@Tag(name = "Tap", description = "탭 적립 및 당일 상태 API")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
public class TapController {

    private final TapBatchService tapBatchService;
    private final TapStatusService tapStatusService;

    @Operation(summary = "탭 배치 제출", description = "클라이언트가 누적한 탭 수를 배치로 제출합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "제출 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "요청 값 오류", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 오류", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "429", description = "탭 제출 속도 제한", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "503", description = "탭 처리 저장소 오류", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @PostMapping("/batches")
    public ResponseEntity<ApiResponse<TapBatchSubmitResponse>> submitBatch(
            @Valid @RequestBody TapBatchSubmitRequest request,
            @Parameter(hidden = true) HttpServletRequest httpServletRequest
    ) {
        var userId = AuthRequestAttributes.getRequiredUserId(httpServletRequest);
        return ResponseEntity.ok(ApiResponse.success(tapBatchService.submitBatch(userId, request)));
    }

    @Operation(summary = "오늘 탭 상태 조회")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 오류", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @GetMapping("/today")
    public ResponseEntity<ApiResponse<TapTodayStatusResponse>> today(@Parameter(hidden = true) HttpServletRequest httpServletRequest) {
        var userId = AuthRequestAttributes.getRequiredUserId(httpServletRequest);
        return ResponseEntity.ok(ApiResponse.success(tapStatusService.getTodayStatus(userId)));
    }
}
