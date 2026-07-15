package com.ggukmoney.beanzip.domain.keycap.controller;

import com.ggukmoney.beanzip.domain.keycap.dto.response.KeycapEquipResponse;
import com.ggukmoney.beanzip.domain.keycap.dto.response.KeycapListResponse;
import com.ggukmoney.beanzip.domain.keycap.dto.response.MyKeycapListResponse;
import com.ggukmoney.beanzip.domain.keycap.service.KeycapService;
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
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping(ApiPaths.KEYCAPS)
@Tag(name = "Keycaps", description = "키캡 카탈로그 및 보유 키캡 API")
public class KeycapController {

    private final KeycapService keycapService;

    @Operation(summary = "키캡 목록 조회", description = "전체 활성 키캡 카탈로그를 조회합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공")
    })
    @GetMapping
    public ResponseEntity<ApiResponse<KeycapListResponse>> getKeycaps() {
        return ResponseEntity.ok(ApiResponse.success(keycapService.getKeycaps()));
    }

    @Operation(summary = "내 키캡 목록 조회", security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
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

    @Operation(summary = "키캡 장착", description = "완성한 보유 키캡을 현재 장착 키캡으로 설정합니다.", security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "장착 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "완성되지 않은 키캡", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
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
