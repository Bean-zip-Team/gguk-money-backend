package com.ggukmoney.beanzip.domain.keycap.controller;

import com.ggukmoney.beanzip.domain.keycap.dto.response.KeycapDrawResponse;
import com.ggukmoney.beanzip.domain.keycap.dto.response.KeycapEquipResponse;
import com.ggukmoney.beanzip.domain.keycap.dto.response.KeycapListResponse;
import com.ggukmoney.beanzip.domain.keycap.dto.response.KeycapPassiveSettleResponse;
import com.ggukmoney.beanzip.domain.keycap.dto.response.KeycapPassiveStatusResponse;
import com.ggukmoney.beanzip.domain.keycap.dto.response.MyKeycapListResponse;
import com.ggukmoney.beanzip.domain.keycap.service.KeycapDrawService;
import com.ggukmoney.beanzip.domain.keycap.service.KeycapPassiveService;
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
    private final KeycapPassiveService passiveService;

    @Operation(summary = "키캡 패시브 조회", description = "현재 장착 키캡의 효과, 미수령 자동 클릭, 적립 상한을 조회해요. 지급하거나 정산 체크포인트를 변경하지 않아요. 패시브가 꺼져 있어도 비활성화 전에 쌓인 몫은 조회돼요.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회에 성공했어요."),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증에 실패했어요. 액세스 토큰이 없거나 유효하지 않아요.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "503", description = "패시브 정책을 확인할 수 없어요. `PASSIVE_POLICY_UNAVAILABLE` 또는 `KEYCAP_ECONOMY_POLICY_UNAVAILABLE` 오류로 응답해요.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @GetMapping("/passive")
    public ResponseEntity<ApiResponse<KeycapPassiveStatusResponse>> passive(
            @Parameter(hidden = true) HttpServletRequest request) {
        return ResponseEntity.ok(ApiResponse.success(passiveService.status(AuthRequestAttributes.getRequiredUserId(request))));
    }

    @Operation(summary = "자동 클릭 정산", description = "인증 후 앱 진입 시 호출해요. 미수령 자동 클릭을 정산하고 포인트와 조각 지급, 체크포인트, 멱등 이력을 함께 저장해요. 같은 멱등키로 다시 호출하면 추가 지급 없이 최초 응답을 돌려줘요. 재응답의 잔액과 시각은 최초 정산 당시 값이므로 지급 표시를 중복 적용하지 마세요. 패시브가 꺼져 있어도 비활성화 전에 쌓인 몫은 지급해요. 경제 전환 전에는 정산 수량과 조각 잔액이 0이며 체크포인트와 멱등 이력을 저장하지 않아요. 따라서 전환 후 같은 키를 쓰면 새 정산으로 처리해요. 새 경제의 첫 정산은 패시브 OFF 상태에서도 체크포인트를 만들며 생성 이전 시간은 소급하지 않아요.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "정산에 성공했거나 최초 정산 결과를 다시 돌려줬어요."),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "멱등키가 없거나 공백이거나 100자를 넘어요.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증에 실패했어요. 액세스 토큰이 없거나 유효하지 않아요.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "정산에 필요한 사용자, 포인트 계정, 조각 지갑 또는 탭 진행 정보가 없어요.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "503", description = "패시브 정책을 확인할 수 없어요. `PASSIVE_POLICY_UNAVAILABLE` 또는 `KEYCAP_ECONOMY_POLICY_UNAVAILABLE` 오류로 응답하며 체크포인트와 잔액은 변경하지 않아요.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @PostMapping("/passive/settle")
    public ResponseEntity<ApiResponse<KeycapPassiveSettleResponse>> settle(
            @Parameter(in = ParameterIn.HEADER, description = "정산 요청 멱등키예요. 공백이 아닌 1~100자이며 같은 요청 재시도에는 같은 값을 사용해요.", required = true, example = "passive-20261012-0001", schema = @Schema(minLength = 1, maxLength = 100))
            @RequestHeader(value = "Idempotency-Key", required = false) String key,
            @Parameter(hidden = true) HttpServletRequest request) {
        return ResponseEntity.ok(ApiResponse.success(passiveService.settle(AuthRequestAttributes.getRequiredUserId(request),key)));
    }

    @Operation(summary = "키캡 목록 조회", description = "경제 전환 전에는 기존 활성 키캡 목록을, 전환 후에는 상시(`BOX`) 키캡 카탈로그를 조회해요. 전환 후 미보유 시즌(`EVENT`) 키캡은 내려주지 않아요. 보유한 시즌 키캡은 내 키캡 목록에서만 보여요. 도감 진행도의 분모는 이 목록의 길이예요.", security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회에 성공했어요."),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "503", description = "경제 정책을 조회할 수 없어요(KEYCAP_ECONOMY_POLICY_UNAVAILABLE).", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증에 실패했어요. 액세스 토큰이 없거나 유효하지 않아요.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @GetMapping
    public ResponseEntity<ApiResponse<KeycapListResponse>> getKeycaps() {
        return ResponseEntity.ok(ApiResponse.success(keycapService.getKeycaps()));
    }

    @Operation(summary = "내 키캡 목록 조회", description = "경제 전환 전에는 수집 중 카드와 종별 조각도 포함하고, 전환 후에는 보유한 키캡과 레벨을 조회해요. 보유한 시즌 키캡도 포함돼요.", security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회에 성공했어요."),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "503", description = "경제 정책을 조회할 수 없어요(KEYCAP_ECONOMY_POLICY_UNAVAILABLE).", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증에 실패했어요. 액세스 토큰이 없거나 유효하지 않아요.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<MyKeycapListResponse>> getMyKeycaps(@Parameter(hidden = true) HttpServletRequest httpServletRequest) {
        return ResponseEntity.ok(ApiResponse.success(keycapService.getMyKeycaps(
                AuthRequestAttributes.getRequiredUserId(httpServletRequest)
        )));
    }

    @Operation(summary = "키캡 뽑기",
            description = "경제 전환 후 조각을 뽑기 가격만큼 차감하고 상시 키캡 중 하나를 등급 가중치로 뽑아요. "
                    + "뽑기 가격은 `keycap.draw.price` 설정으로 정하고 기본값은 5예요. "
                    + "등급 가중치는 `COMMON` 60%, `RARE` 28%, `EPIC` 10%, `LEGENDARY` 2%예요. "
                    + "이미 보유한 키캡이 나오면 레벨이 1 올라요. "
                    + "레벨이 바뀌기 전에 기존 장착 효과로 자동 클릭을 정산해요. "
                    + "같은 `Idempotency-Key`로 다시 호출하면 뽑기 결과는 최초 결과를, 조각 잔액은 조회 시점 값을 돌려줘요.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "뽑기에 성공했어요. 같은 멱등키로 다시 호출하면 뽑기 결과는 최초 결과를, 잔액은 조회 시점 값을 돌려줘요."),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "멱등키가 없거나 조각이 부족해요.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증에 실패했어요. 액세스 토큰이 없거나 유효하지 않아요.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "사용자나 정산에 필요한 포인트 계정, 조각 지갑, 탭 진행 정보가 없어요.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "경제 전환 전에는 뽑기를 사용할 수 없거나 뽑을 수 있는 상시 키캡이 없어요.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "503", description = "자동 클릭 정산에 필요한 패시브 정책을 확인할 수 없어요. `PASSIVE_POLICY_UNAVAILABLE` 또는 `KEYCAP_ECONOMY_POLICY_UNAVAILABLE` 오류로 응답하며 뽑기와 지급은 함께 롤백돼요.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
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

    @Operation(summary = "키캡 장착", description = "보유한 키캡을 현재 장착 키캡으로 설정해요. 기존 장착 효과로 자동 클릭을 먼저 정산한 뒤 기존 키캡을 해제하고 새 키캡을 장착해요.", security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "장착에 성공했어요."),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "진행 중이라 아직 보유하지 않은 키캡이에요.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증에 실패했어요. 액세스 토큰이 없거나 유효하지 않아요.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "보유한 키캡 중에 해당 키캡이 없거나 사용자, 포인트 계정, 조각 지갑, 탭 진행 정보가 없어요.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "503", description = "자동 클릭 정산에 필요한 패시브 정책을 확인할 수 없어요. `PASSIVE_POLICY_UNAVAILABLE` 또는 `KEYCAP_ECONOMY_POLICY_UNAVAILABLE` 오류로 응답하며 장착 상태와 잔액은 변경하지 않아요.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
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
