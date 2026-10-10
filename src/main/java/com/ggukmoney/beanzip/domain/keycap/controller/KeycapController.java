package com.ggukmoney.beanzip.domain.keycap.controller;

import com.ggukmoney.beanzip.domain.keycap.dto.response.KeycapDrawResponse;
import com.ggukmoney.beanzip.domain.keycap.dto.response.KeycapEquipResponse;
import com.ggukmoney.beanzip.domain.keycap.dto.response.KeycapListResponse;
import com.ggukmoney.beanzip.domain.keycap.dto.response.MyKeycapListResponse;
import com.ggukmoney.beanzip.domain.keycap.service.KeycapDrawService;
import com.ggukmoney.beanzip.domain.keycap.service.KeycapService;
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
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/keycaps")
@Tag(name = "Keycaps", description = "키캡 카탈로그, 보유 키캡, 뽑기 API")
public class KeycapController {

    private final KeycapService keycapService;
    private final KeycapDrawService keycapDrawService;
    private final com.ggukmoney.beanzip.domain.keycap.service.KeycapPassiveService passiveService;

    @Operation(summary = "키캡 패시브 조회", description = "현재 효과와 미수령 자동 클릭을 조회합니다. 지급하지 않습니다.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @GetMapping("/passive")
    public ResponseEntity<ApiResponse<com.ggukmoney.beanzip.domain.keycap.dto.response.KeycapPassiveStatusResponse>> passive(
            @Parameter(hidden = true) HttpServletRequest request) {
        return ResponseEntity.ok(ApiResponse.success(passiveService.status(AuthRequestAttributes.getRequiredUserId(request))));
    }

    @Operation(summary = "자동 클릭 정산", description = "앱 진입 시 호출합니다. 같은 멱등키는 저장된 지급 결과를 재생합니다.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @PostMapping("/passive/settle")
    public ResponseEntity<ApiResponse<com.ggukmoney.beanzip.domain.keycap.dto.response.KeycapPassiveSettleResponse>> settle(
            @RequestHeader(value = "Idempotency-Key", required = false) String key,
            @Parameter(hidden = true) HttpServletRequest request) {
        return ResponseEntity.ok(ApiResponse.success(passiveService.settle(AuthRequestAttributes.getRequiredUserId(request),key)));
    }

    @Operation(summary = "키캡 목록 조회", description = "상시(BOX) 키캡 카탈로그를 조회합니다. 시즌(EVENT) 키캡은 내려주지 않으며, 보유한 시즌 키캡은 내 키캡 목록에서만 보입니다. 도감 진행도의 분모는 이 목록의 길이입니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공")
    })
    @GetMapping
    public ResponseEntity<ApiResponse<KeycapListResponse>> getKeycaps() {
        return ResponseEntity.ok(ApiResponse.success(keycapService.getKeycaps()));
    }

    @Operation(summary = "내 키캡 목록 조회", description = "보유한 키캡과 레벨을 조회합니다. 보유한 시즌 키캡도 포함됩니다.", security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 오류", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<MyKeycapListResponse>> getMyKeycaps(@Parameter(hidden = true) HttpServletRequest httpServletRequest) {
        return ResponseEntity.ok(ApiResponse.success(keycapService.getMyKeycaps(
                AuthRequestAttributes.getRequiredUserId(httpServletRequest)
        )));
    }

    @Operation(summary = "키캡 뽑기",
            description = "조각을 뽑기 가격(`keycap.draw.price`, 기본 5)만큼 차감하고 상시 키캡 중 하나를 등급 가중(60/28/10/2%)으로 뽑습니다. "
                    + "이미 보유한 키캡이 나오면 레벨이 1 오릅니다. 같은 Idempotency-Key 재호출은 처음 결과를 그대로 돌려줍니다.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "뽑기 성공 또는 멱등 재응답"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "멱등키 누락 또는 조각 부족", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 오류", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "지갑 없음", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "뽑을 키캡 없음", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @PostMapping("/draw")
    public ResponseEntity<ApiResponse<KeycapDrawResponse>> draw(
            @Parameter(in = ParameterIn.HEADER, description = "뽑기 요청 멱등키. 최대 100자이며 같은 요청 재시도에 같은 값을 사용합니다.", required = true, example = "draw-20261009-0001")
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Parameter(hidden = true) HttpServletRequest httpServletRequest
    ) {
        return ResponseEntity.ok(ApiResponse.success(keycapDrawService.draw(
                AuthRequestAttributes.getRequiredUserId(httpServletRequest),
                idempotencyKey
        )));
    }

    @Operation(summary = "키캡 장착", description = "보유 키캡을 현재 장착 키캡으로 설정합니다.", security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "장착 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 오류", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "보유 키캡 없음", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @PutMapping("/{keycapId}/equip")
    public ResponseEntity<ApiResponse<KeycapEquipResponse>> equipKeycap(
            @Parameter(hidden = true) HttpServletRequest httpServletRequest,
            @Parameter(description = "장착할 키캡 ID", example = "11111111-1111-1111-1111-111111111111")
            @PathVariable UUID keycapId
    ) {
        return ResponseEntity.ok(ApiResponse.success(keycapService.equipKeycap(
                AuthRequestAttributes.getRequiredUserId(httpServletRequest),
                keycapId
        )));
    }
}
