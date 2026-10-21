package com.ggukmoney.beanzip.domain.keycap.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "키캡 뽑기 응답이에요.")
public record KeycapDrawResponse(
        @Schema(description = "뽑기 ID예요.", example = "11111111-1111-1111-1111-111111111111")
        UUID drawId,
        @Schema(description = "뽑힌 키캡 ID예요.", example = "22222222-2222-2222-2222-222222222222")
        UUID keycapId,
        @Schema(description = "키캡 코드예요.", example = "BASIC_BEAN")
        String code,
        @Schema(description = "키캡 이름이에요.", example = "기본 콩")
        String name,
        @Schema(description = "키캡 등급이에요. `COMMON`, `RARE`, `EPIC`, `LEGENDARY` 중 하나예요.", example = "COMMON")
        String grade,
        @Schema(description = "이미지 URL이에요.", example = "https://example.com/keycap.png")
        String imageUrl,
        @Schema(description = "사운드 URL이에요.", example = "https://example.com/keycap.mp3")
        String soundUrl,
        @Schema(description = "상세 화면의 사운드 항목에 표시할 이름이에요. 지금은 키캡 이름과 같아요.", example = "기본 콩")
        String soundName,
        @Schema(description = "처음 얻은 키캡인지 여부예요. false면 이미 보유한 키캡이라 레벨만 올랐어요.", example = "false")
        boolean newlyAcquired,
        @Schema(description = "뽑기 후 이 키캡의 레벨이에요. 같은 멱등키로 재호출해도 처음 뽑았을 때의 레벨을 돌려줘요.", example = "9")
        int level,
        @Schema(description = "이번 뽑기에 쓴 조각 개수예요.", example = "5")
        int shardsSpent,
        @Schema(description = "뽑기 후 조각 잔액이에요. 같은 멱등키로 재호출한 응답에서는 조회 시점의 잔액이에요.", example = "12")
        int shardBalance,
        @Schema(description = "뽑은 시각이에요.", example = "2026-10-09T01:00:00Z")
        Instant drawnAt
) {
}
