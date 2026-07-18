package com.ggukmoney.beanzip.domain.onboarding.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Schema(description = "온보딩 키캡 상자 개봉 응답")
public record OnboardingKeycapBoxOpenResponse(
        @Schema(description = "온보딩 보상 시도 ID", example = "11111111-1111-1111-1111-111111111111")
        UUID onboardingAttemptId,
        @Schema(description = "지급된 키캡 목록 (고정 키캡 1개 + 랜덤 보너스 키캡 1개)")
        List<KeycapSummary> keycaps,
        @Schema(description = "완성 여부", example = "true")
        boolean completed,
        @Schema(description = "수령 가능 보상 포인트", example = "2")
        int rewardPoint,
        @Schema(description = "개봉 시각", example = "2026-07-15T01:00:05Z")
        Instant openedAt,
        @Schema(description = "보상 만료 시각", example = "2026-07-15T01:15:05Z")
        Instant expiresAt
) {
    @Schema(description = "지급된 키캡 정보")
    public record KeycapSummary(
            @Schema(description = "키캡 ID", example = "22222222-2222-2222-2222-222222222222")
            UUID keycapId,
            @Schema(description = "키캡 코드", example = "main")
            String code,
            @Schema(description = "키캡 이름", example = "메인 키캡")
            String name,
            @Schema(description = "키캡 등급", example = "COMMON")
            String grade,
            @Schema(description = "이미지 URL", example = "https://example.com/keycaps/main.webp")
            String imageUrl,
            @Schema(description = "사운드 URL", example = "https://example.com/keycaps/main.mp3")
            String soundUrl
    ) {
    }
}
