package com.ggukmoney.beanzip.domain.cashout.controller;

import com.ggukmoney.beanzip.domain.cashout.dto.response.CashoutListItemResponse;
import com.ggukmoney.beanzip.domain.cashout.dto.response.CashoutListPageResponse;
import com.ggukmoney.beanzip.domain.cashout.dto.response.CashoutQuoteResponse;
import com.ggukmoney.beanzip.domain.cashout.dto.response.CashoutSubmitResponse;
import com.ggukmoney.beanzip.domain.cashout.entity.CashoutRequest;
import com.ggukmoney.beanzip.domain.cashout.service.CashoutService;
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
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping(ApiPaths.CASHOUTS)
@Tag(name = "Cashouts", description = "포인트 출금 견적, 신청, 조회 API")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
public class CashoutController {

    private final CashoutService cashoutService;

    @Operation(summary = "출금 견적 조회", description = "현재 포인트 잔액 기준 Toss 포인트 전환 예상 금액을 조회합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 오류", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @GetMapping("/quote")
    public ResponseEntity<ApiResponse<CashoutQuoteResponse>> quote(@Parameter(hidden = true) HttpServletRequest httpServletRequest) {
        var userId = AuthRequestAttributes.getRequiredUserId(httpServletRequest);
        return ResponseEntity.ok(ApiResponse.success(cashoutService.getQuote(userId)));
    }

    @Operation(summary = "출금 신청", description = "현재 포인트 잔액 전체를 Toss 포인트로 전환 신청합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "202", description = "출금 신청 접수 또는 멱등 재응답"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "멱등키 누락/형식 오류 또는 최소 포인트 미달", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 오류", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "처리 중인 출금 존재", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @PostMapping
    public ResponseEntity<ApiResponse<CashoutSubmitResponse>> submit(
            @Parameter(in = ParameterIn.HEADER, description = "출금 신청 멱등키 UUID", required = true, example = "11111111-1111-1111-1111-111111111111")
            @RequestHeader("Idempotency-Key") UUID idempotencyKey,
            @Parameter(hidden = true) HttpServletRequest httpServletRequest
    ) {
        var userId = AuthRequestAttributes.getRequiredUserId(httpServletRequest);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(ApiResponse.success(cashoutService.submit(userId, idempotencyKey)));
    }

    @Operation(summary = "출금 목록 조회", description = "커서 기반으로 내 출금 신청 목록을 조회합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "잘못된 cursor 또는 query 값", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 오류", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @GetMapping
    public ResponseEntity<ApiResponse<CashoutListPageResponse>> list(
            @Parameter(description = "다음 페이지 커서", example = "MTIz")
            @RequestParam(required = false) String cursor,
            @Parameter(description = "페이지 크기. 1보다 작으면 1, 최대값보다 크면 최대값으로 보정됩니다.", example = "20")
            @RequestParam(required = false) Integer size,
            @Parameter(description = "출금 상태 필터", example = "PROCESSING")
            @RequestParam(required = false) CashoutRequest.Status status,
            @Parameter(hidden = true) HttpServletRequest httpServletRequest
    ) {
        var userId = AuthRequestAttributes.getRequiredUserId(httpServletRequest);
        return ResponseEntity.ok(ApiResponse.success(cashoutService.list(userId, cursor, size, status)));
    }

    @Operation(summary = "출금 상세 조회")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 오류", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "출금 요청 없음", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @GetMapping("/{cashoutId}")
    public ResponseEntity<ApiResponse<CashoutListItemResponse>> detail(
            @Parameter(description = "출금 요청 ID", example = "11111111-1111-1111-1111-111111111111")
            @PathVariable UUID cashoutId,
            @Parameter(hidden = true) HttpServletRequest httpServletRequest
    ) {
        var userId = AuthRequestAttributes.getRequiredUserId(httpServletRequest);
        return ResponseEntity.ok(ApiResponse.success(cashoutService.getDetail(userId, cashoutId)));
    }
}
