package com.ggukmoney.beanzip.domain.point.controller;

import com.ggukmoney.beanzip.domain.point.dto.response.PointLedgerPageResponse;
import com.ggukmoney.beanzip.domain.point.dto.response.PointMeResponse;
import com.ggukmoney.beanzip.domain.point.entity.PointLedger;
import com.ggukmoney.beanzip.domain.point.service.PointStatusService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/points")
@Tag(name = "Points", description = "포인트 잔액 및 원장 API")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
public class PointController {

    private final PointStatusService pointStatusService;

    @Operation(summary = "내 포인트 상태 조회")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 오류", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "포인트 계정 없음", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<PointMeResponse>> me(@Parameter(hidden = true) HttpServletRequest httpServletRequest) {
        var userId = AuthRequestAttributes.getRequiredUserId(httpServletRequest);
        return ResponseEntity.ok(ApiResponse.success(pointStatusService.getMyPoints(userId)));
    }

    @Operation(summary = "포인트 원장 조회", description = "커서, 유형, 사유, 기간으로 내 포인트 원장을 조회합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "잘못된 cursor 또는 query 값", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 오류", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @GetMapping("/ledger")
    public ResponseEntity<ApiResponse<PointLedgerPageResponse>> ledger(
            @Parameter(description = "다음 페이지 커서", example = "MTIz")
            @RequestParam(required = false) String cursor,
            @Parameter(description = "페이지 크기. 1보다 작으면 1, 최대값보다 크면 최대값으로 보정됩니다.", example = "20")
            @RequestParam(required = false) Integer size,
            @Parameter(description = "원장 항목 유형", example = "CREDIT")
            @RequestParam(required = false) PointLedger.EntryType entryType,
            @Parameter(description = "원장 사유 필터", example = "TAP")
            @RequestParam(required = false) String reason,
            @Parameter(description = "조회 시작 시각", example = "2026-07-15T00:00:00Z")
            @RequestParam(required = false) Instant from,
            @Parameter(description = "조회 종료 시각", example = "2026-07-16T00:00:00Z")
            @RequestParam(required = false) Instant to,
            @Parameter(hidden = true) HttpServletRequest httpServletRequest
    ) {
        var userId = AuthRequestAttributes.getRequiredUserId(httpServletRequest);
        return ResponseEntity.ok(ApiResponse.success(
                pointStatusService.getLedger(userId, cursor, size, entryType, reason, from, to)
        ));
    }
}
