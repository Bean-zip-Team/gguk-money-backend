package com.ggukmoney.beanzip.domain.promotion.controller;

import com.ggukmoney.beanzip.domain.promotion.dto.response.MissionListResponse;
import com.ggukmoney.beanzip.domain.promotion.service.MissionQueryService;
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
import org.springframework.web.bind.annotation.RestController;

/**
 * 유저용 미션 목록 (BEA-292).
 *
 * <p>토스 혜택탭에서 미션을 보고 들어온 유저가 앱 안에서 진행도를 확인하는 경로다. 지금은 랜딩이
 * 홈이라 무엇을 하러 왔는지 알 방법이 없어 퍼널이 문 앞에서 끊긴다.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/missions")
@Tag(name = "Mission", description = "미션 목록 API")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
public class MissionController {

    private final MissionQueryService missionQueryService;

    @Operation(summary = "미션 목록 조회",
            description = "진행 중인 미션과 내 진행도, 달성·지급 상태를 조회합니다. "
                    + "꺼진 미션은 목록에서 빠지지만, 이미 받은 미션은 계속 노출됩니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 오류", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @GetMapping
    public ResponseEntity<ApiResponse<MissionListResponse>> getMissions(
            @Parameter(hidden = true) HttpServletRequest httpServletRequest
    ) {
        return ResponseEntity.ok(ApiResponse.success(missionQueryService.missionsOf(
                AuthRequestAttributes.getRequiredUserId(httpServletRequest)
        )));
    }
}
