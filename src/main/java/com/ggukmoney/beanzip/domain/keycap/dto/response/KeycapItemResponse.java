package com.ggukmoney.beanzip.domain.keycap.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

@Schema(description = "키캡 카탈로그 항목이에요.")
public record KeycapItemResponse(
        @Schema(description = "키캡 ID예요.", example = "11111111-1111-1111-1111-111111111111")
        UUID keycapId,
        @Schema(description = "키캡 코드예요.", example = "BASIC_BEAN")
        String code,
        @Schema(description = "키캡 이름이에요.", example = "기본 콩")
        String name,
        @Schema(description = "키캡 등급이에요. `COMMON`, `RARE`, `EPIC`, `LEGENDARY` 중 하나예요.", example = "COMMON")
        String grade,
        @Schema(description = "[deprecated] 조각을 모아 완성하는 방식은 사라졌어요(BEA-329). 뽑기 가격은 조각 상태 응답의 `drawPrice` 필드를 보세요.", example = "5", deprecated = true)
        int requiredShardCount,
        @Schema(description = "도감 회차를 뜻하는 시즌 번호예요.", example = "1")
        int season,
        @Schema(description = "획득 경로예요. `BOX`는 상시 뽑기, `EVENT`는 시즌 한정 지급이에요. 카탈로그 목록에는 `BOX` 키캡만 내려가요.", example = "BOX")
        String acquisitionType,
        @Schema(description = "이미지 URL이에요.", example = "https://example.com/keycap.png")
        String imageUrl,
        @Schema(description = "사운드 URL이에요.", example = "https://example.com/keycap.mp3")
        String soundUrl,
        @Schema(description = "미보유 카드에 표시할 Lv1 효과 미리보기예요. 실제 보유 효과는 내 키캡의 `effects` 필드를 보세요.")
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
