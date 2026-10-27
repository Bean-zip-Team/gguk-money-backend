package com.ggukmoney.beanzip.domain.onboarding.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "온보딩 키캡 상자 개봉 응답이에요.")
public record OnboardingKeycapBoxOpenResponse(
        @Schema(description = "온보딩 보상 시도 ID예요.", example = "11111111-1111-1111-1111-111111111111")
        UUID onboardingAttemptId,
        @Schema(description = "상자를 까서 나온 랜덤 보너스 키캡 ID예요. 고정 키캡(main)은 응답에 포함되지 않아요. 고정 키캡은 로그인할 때 따로 지급돼요.", example = "22222222-2222-2222-2222-222222222222")
        UUID keycapId,
        @Schema(description = "키캡 코드예요.", example = "cheer")
        String code,
        @Schema(description = "키캡 이름이에요.", example = "치어 키캡")
        String name,
        @Schema(description = "키캡 등급이에요. `COMMON`, `RARE`, `EPIC`, `LEGENDARY` 중 하나예요.", example = "COMMON")
        String grade,
        @Schema(description = "이미지 URL이에요.", example = "https://example.com/keycaps/cheer.webp")
        String imageUrl,
        @Schema(description = "사운드 URL이에요.", example = "https://example.com/keycaps/cheer.mp3")
        String soundUrl,
        @Schema(description = "완성 여부예요. 항상 true예요.", example = "true")
        boolean completed,
        @Schema(description = "수령할 수 있는 보상 포인트예요.", example = "2")
        int rewardPoint,
        @Schema(description = "개봉 시각이에요.", example = "2026-07-15T01:00:05Z")
        Instant openedAt,
        @Schema(description = "보상 만료 시각이에요. 개봉 시각에 `onboarding.reward.attemptTtlSeconds` 설정값(초)을 더한 시각이에요.", example = "2026-07-15T01:15:05Z")
        Instant expiresAt
) {
}
