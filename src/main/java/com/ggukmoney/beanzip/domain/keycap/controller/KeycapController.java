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
@Tag(name = "Keycaps", description = "키캡 카탈로그, 보유 키캡, 뽑기 API예요.")
public class KeycapController {

    private final KeycapService keycapService;
    private final KeycapDrawService keycapDrawService;

    @Operation(summary = "키캡 목록 조회", description = "상시(`BOX`) 키캡 카탈로그를 조회해요. 시즌(`EVENT`) 키캡은 내려주지 않아요. 보유한 시즌 키캡은 내 키캡 목록에서만 보여요. 도감 진행도의 분모는 이 목록의 길이예요.", security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회에 성공했어요."),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증에 실패했어요. 액세스 토큰이 없거나 유효하지 않아요.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @GetMapping
    public ResponseEntity<ApiResponse<KeycapListResponse>> getKeycaps() {
        return ResponseEntity.ok(ApiResponse.success(keycapService.getKeycaps()));
    }

    @Operation(summary = "내 키캡 목록 조회", description = "보유한 키캡과 레벨을 조회해요. 보유한 시즌 키캡도 포함돼요.", security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회에 성공했어요."),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증에 실패했어요. 액세스 토큰이 없거나 유효하지 않아요.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<MyKeycapListResponse>> getMyKeycaps(@Parameter(hidden = true) HttpServletRequest httpServletRequest) {
        return ResponseEntity.ok(ApiResponse.success(keycapService.getMyKeycaps(
                AuthRequestAttributes.getRequiredUserId(httpServletRequest)
        )));
    }

    @Operation(summary = "키캡 뽑기",
            description = "조각을 뽑기 가격만큼 차감하고 상시 키캡 중 하나를 등급 가중치로 뽑아요. "
                    + "뽑기 가격은 `keycap.draw.price` 설정으로 정하고 기본값은 5예요. "
                    + "등급 가중치는 `COMMON` 60%, `RARE` 28%, `EPIC` 10%, `LEGENDARY` 2%예요. "
                    + "이미 보유한 키캡이 나오면 레벨이 1 올라요. "
                    + "같은 `Idempotency-Key` 헤더로 다시 호출하면 처음 결과를 그대로 돌려줘요.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "뽑기에 성공했어요. 같은 멱등키로 다시 호출한 경우에도 처음 결과와 함께 200을 돌려줘요."),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "멱등키가 없거나 조각이 부족해요.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증에 실패했어요. 액세스 토큰이 없거나 유효하지 않아요.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "조각 지갑이 없어요.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "뽑을 수 있는 상시 키캡이 없어요.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @PostMapping("/draw")
    public ResponseEntity<ApiResponse<KeycapDrawResponse>> draw(
            @Parameter(in = ParameterIn.HEADER, description = "뽑기 요청 멱등키예요. 최대 100자예요. 같은 요청을 재시도할 때는 같은 값을 써요.", required = true, example = "draw-20261009-0001")
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Parameter(hidden = true) HttpServletRequest httpServletRequest
    ) {
        return ResponseEntity.ok(ApiResponse.success(keycapDrawService.draw(
                AuthRequestAttributes.getRequiredUserId(httpServletRequest),
                idempotencyKey
        )));
    }

    @Operation(summary = "키캡 장착", description = "보유한 키캡을 현재 장착 키캡으로 설정해요. 기존에 장착한 키캡은 자동으로 해제돼요.", security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "장착에 성공했어요."),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "진행 중이라 아직 보유하지 않은 키캡이에요.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증에 실패했어요. 액세스 토큰이 없거나 유효하지 않아요.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "보유한 키캡 중에 해당 키캡이 없어요.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @PutMapping("/{keycapId}/equip")
    public ResponseEntity<ApiResponse<KeycapEquipResponse>> equipKeycap(
            @Parameter(hidden = true) HttpServletRequest httpServletRequest,
            @Parameter(description = "장착할 키캡 ID예요.", example = "11111111-1111-1111-1111-111111111111")
            @PathVariable UUID keycapId
    ) {
        return ResponseEntity.ok(ApiResponse.success(keycapService.equipKeycap(
                AuthRequestAttributes.getRequiredUserId(httpServletRequest),
                keycapId
        )));
    }
}
