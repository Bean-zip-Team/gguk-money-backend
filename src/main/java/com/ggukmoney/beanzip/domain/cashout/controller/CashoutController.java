package com.ggukmoney.beanzip.domain.cashout.controller;

import com.ggukmoney.beanzip.domain.cashout.dto.response.CashoutListItemResponse;
import com.ggukmoney.beanzip.domain.cashout.dto.response.CashoutListPageResponse;
import com.ggukmoney.beanzip.domain.cashout.dto.response.CashoutQuoteResponse;
import com.ggukmoney.beanzip.domain.cashout.dto.response.CashoutSubmitResponse;
import com.ggukmoney.beanzip.domain.cashout.entity.CashoutRequest;
import com.ggukmoney.beanzip.domain.cashout.service.CashoutService;
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
@RequestMapping("/api/cashouts")
@Tag(name = "Cashouts", description = "포인트 출금 견적, 신청, 조회를 다루는 API예요.")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
public class CashoutController {

    private final CashoutService cashoutService;

    @Operation(summary = "출금 견적 조회", description = "현재 포인트 잔액을 기준으로 Toss 포인트 전환 예상 금액을 조회해요. 실제로 차감할 포인트와 출금 뒤 남는 포인트도 함께 돌려줘요.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회에 성공했어요."),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증에 실패했어요. 액세스 토큰이 없거나 유효하지 않아요.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @GetMapping("/quote")
    public ResponseEntity<ApiResponse<CashoutQuoteResponse>> quote(@Parameter(hidden = true) HttpServletRequest httpServletRequest) {
        var userId = AuthRequestAttributes.getRequiredUserId(httpServletRequest);
        return ResponseEntity.ok(ApiResponse.success(cashoutService.getQuote(userId)));
    }

    @Operation(summary = "출금 신청", description = "현재 포인트 잔액을 Toss 포인트로 전환 신청해요. 잔액 전체가 아니라 지급액을 만드는 데 필요한 포인트만 차감하고, 나머지는 잔액에 남아요. Toss 지급 요청까지 마친 뒤 응답해요. Toss 지급 요청이 실패하면 차감한 포인트를 환불하고 `status` 필드가 `FAILED`인 응답을 돌려줘요.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "202", description = "출금 신청을 접수했어요. 같은 멱등키로 다시 요청하면 처음 접수한 결과를 다시 돌려줘요."),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "멱등키가 없거나 형식이 올바르지 않아요. 잔액이 최소 출금 포인트에 못 미쳐도 이 응답을 받아요.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증에 실패했어요. 액세스 토큰이 없거나 유효하지 않아요.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "이미 처리 중인 출금 신청이 있어요. `REQUESTED` 또는 `PROCESSING` 상태인 출금이 끝나야 새로 신청할 수 있어요.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @PostMapping
    public ResponseEntity<ApiResponse<CashoutSubmitResponse>> submit(
            @Parameter(in = ParameterIn.HEADER, description = "출금 신청 멱등키예요. UUID 형식으로 보내요. 같은 키로 다시 보내면 새로 신청하지 않고 처음 결과를 돌려줘요.", required = true, example = "11111111-1111-1111-1111-111111111111")
            @RequestHeader("Idempotency-Key") UUID idempotencyKey,
            @Parameter(hidden = true) HttpServletRequest httpServletRequest
    ) {
        var userId = AuthRequestAttributes.getRequiredUserId(httpServletRequest);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(ApiResponse.success(cashoutService.submit(userId, idempotencyKey)));
    }

    @Operation(summary = "출금 목록 조회", description = "커서 기반으로 내 출금 신청 목록을 조회해요. 최신 신청부터 정렬해요.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회에 성공했어요."),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "`cursor` 값이나 쿼리 파라미터 값이 올바르지 않아요.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증에 실패했어요. 액세스 토큰이 없거나 유효하지 않아요.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @GetMapping
    public ResponseEntity<ApiResponse<CashoutListPageResponse>> list(
            @Parameter(description = "다음 페이지 커서예요. 이전 응답의 `nextCursor` 값을 그대로 보내요. 보내지 않으면 첫 페이지를 조회해요.", example = "MTIz")
            @RequestParam(required = false) String cursor,
            @Parameter(description = "페이지 크기예요. 기본값은 20, 최대값은 100이에요. 1보다 작으면 1, 최대값보다 크면 최대값으로 보정돼요.", example = "20")
            @RequestParam(required = false) Integer size,
            @Parameter(description = "출금 상태 필터예요. `REQUESTED`는 접수, `PROCESSING`은 Toss 지급 확인 중, `SUCCEEDED`는 지급 완료, `FAILED`는 지급 실패, `CANCELED`는 취소예요.", example = "PROCESSING")
            @RequestParam(required = false) CashoutRequest.Status status,
            @Parameter(hidden = true) HttpServletRequest httpServletRequest
    ) {
        var userId = AuthRequestAttributes.getRequiredUserId(httpServletRequest);
        return ResponseEntity.ok(ApiResponse.success(cashoutService.list(userId, cursor, size, status)));
    }

    @Operation(summary = "출금 상세 조회", description = "출금 요청 하나를 조회해요. 내 출금 요청만 조회할 수 있어요.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회에 성공했어요."),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증에 실패했어요. 액세스 토큰이 없거나 유효하지 않아요.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "출금 요청을 찾을 수 없어요. 다른 사용자의 출금 요청 ID를 보내도 이 응답을 받아요.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @GetMapping("/{cashoutId}")
    public ResponseEntity<ApiResponse<CashoutListItemResponse>> detail(
            @Parameter(description = "조회할 출금 요청 ID예요.", example = "11111111-1111-1111-1111-111111111111")
            @PathVariable UUID cashoutId,
            @Parameter(hidden = true) HttpServletRequest httpServletRequest
    ) {
        var userId = AuthRequestAttributes.getRequiredUserId(httpServletRequest);
        return ResponseEntity.ok(ApiResponse.success(cashoutService.getDetail(userId, cashoutId)));
    }
}
