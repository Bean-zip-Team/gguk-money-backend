package com.ggukmoney.beanzip.domain.keycap.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

@Schema(description = "키캡 카탈로그 항목")
public record KeycapItemResponse(
        @Schema(description = "키캡 ID", example = "11111111-1111-1111-1111-111111111111")
        UUID keycapId,
        @Schema(description = "키캡 코드", example = "BASIC_BEAN")
        String code,
        @Schema(description = "키캡 이름", example = "기본 콩")
        String name,
        @Schema(description = "키캡 등급", example = "COMMON")
        String grade,
        @Schema(description = "완성 필요 조각 수", example = "5")
        int requiredShardCount,
        @Schema(description = "시즌", example = "1")
        int season,
        @Schema(description = "획득 경로. BOX: 상자에서 추첨, EVENT: 이벤트 한정 지급", example = "BOX")
        String acquisitionType,
        @Schema(description = "이미지 URL", example = "https://example.com/keycap.png")
        String imageUrl,
        @Schema(description = "사운드 URL", example = "https://example.com/keycap.mp3")
        String soundUrl,
        @Schema(description = "미보유 카드의 Lv1 미리보기. 실제 보유 효과는 내 키캡의 effects를 사용합니다.")
        KeycapPassiveEffectResponse previewEffects
) {
    public KeycapItemResponse(UUID id,String code,String name,String grade,int requiredShardCount,int season,
            String acquisitionType,String imageUrl,String soundUrl) {
        this(id,code,name,grade,requiredShardCount,season,acquisitionType,imageUrl,soundUrl,null);
    }
    public KeycapItemResponse withPreview(KeycapPassiveEffectResponse value) {
        return new KeycapItemResponse(keycapId,code,name,grade,requiredShardCount,season,acquisitionType,imageUrl,soundUrl,value);
    }
}
