package com.ggukmoney.beanzip.domain.keycap.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "키캡 뽑기 응답")
public record KeycapDrawResponse(
        @Schema(description = "뽑기 ID", example = "11111111-1111-1111-1111-111111111111")
        UUID drawId,
        @Schema(description = "뽑힌 키캡 ID", example = "22222222-2222-2222-2222-222222222222")
        UUID keycapId,
        @Schema(description = "키캡 코드", example = "BASIC_BEAN")
        String code,
        @Schema(description = "키캡 이름", example = "기본 콩")
        String name,
        @Schema(description = "키캡 등급", example = "COMMON")
        String grade,
        @Schema(description = "이미지 URL", example = "https://example.com/keycap.png")
        String imageUrl,
        @Schema(description = "사운드 URL", example = "https://example.com/keycap.mp3")
        String soundUrl,
        @Schema(description = "처음 얻은 키캡인지 여부. false 면 레벨업", example = "false")
        boolean newlyAcquired,
        @Schema(description = "뽑기 후 이 키캡의 레벨", example = "9")
        int level,
        @Schema(description = "이번 뽑기에 쓴 조각 수", example = "5")
        int shardsSpent,
        @Schema(description = "뽑기 후 조각 잔액. 같은 멱등키로 재생된 응답에서는 현재 잔액", example = "12")
        int shardBalance,
        @Schema(description = "뽑은 시각", example = "2026-10-09T01:00:00Z")
        Instant drawnAt
) {
}
