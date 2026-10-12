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
@Tag(name = "Points", description = "포인트 잔액과 원장을 조회하는 API예요.")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
public class PointController {

    private final PointStatusService pointStatusService;

    @Operation(summary = "내 포인트 상태 조회", description = "내 포인트 잔액과 누적 적립, 누적 사용 포인트를 조회해요. 출금 가능 여부와 예상 출금 금액도 함께 돌려줘요.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회에 성공했어요."),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증에 실패했어요. 액세스 토큰이 없거나 유효하지 않아요.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "포인트 계정이 없어요.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<PointMeResponse>> me(@Parameter(hidden = true) HttpServletRequest httpServletRequest) {
        var userId = AuthRequestAttributes.getRequiredUserId(httpServletRequest);
        return ResponseEntity.ok(ApiResponse.success(pointStatusService.getMyPoints(userId)));
    }

    @Operation(summary = "포인트 원장 조회", description = "커서, 유형, 사유, 기간으로 내 포인트 원장을 조회해요. 최신 항목부터 정렬해요.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회에 성공했어요."),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "`cursor` 값이나 쿼리 파라미터 값이 올바르지 않아요.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증에 실패했어요. 액세스 토큰이 없거나 유효하지 않아요.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @GetMapping("/ledger")
    public ResponseEntity<ApiResponse<PointLedgerPageResponse>> ledger(
            @Parameter(description = "다음 페이지 커서예요. 이전 응답의 `nextCursor` 값을 그대로 보내요. 보내지 않으면 첫 페이지를 조회해요.", example = "MTIz")
            @RequestParam(required = false) String cursor,
            @Parameter(description = "페이지 크기예요. 기본값은 20, 최대값은 100이에요. 1보다 작으면 1, 최대값보다 크면 최대값으로 보정돼요.", example = "20")
            @RequestParam(required = false) Integer size,
            @Parameter(description = "원장 항목 유형 필터예요. `CREDIT`은 적립, `DEBIT`은 차감, `REVERSAL`은 출금 실패에 따른 환불이에요.", example = "CREDIT")
            @RequestParam(required = false) PointLedger.EntryType entryType,
            @Parameter(description = "원장 사유 필터예요. 사유 코드가 정확히 같은 항목만 조회해요.", example = "TAP_REWARD")
            @RequestParam(required = false) String reason,
            @Parameter(description = "조회 시작 시각이에요. 이 시각에 발생한 항목도 포함해요.", example = "2026-07-15T00:00:00Z")
            @RequestParam(required = false) Instant from,
            @Parameter(description = "조회 종료 시각이에요. 이 시각에 발생한 항목도 포함해요.", example = "2026-07-16T00:00:00Z")
            @RequestParam(required = false) Instant to,
            @Parameter(hidden = true) HttpServletRequest httpServletRequest
    ) {
        var userId = AuthRequestAttributes.getRequiredUserId(httpServletRequest);
        return ResponseEntity.ok(ApiResponse.success(
                pointStatusService.getLedger(userId, cursor, size, entryType, reason, from, to)
        ));
    }
}
