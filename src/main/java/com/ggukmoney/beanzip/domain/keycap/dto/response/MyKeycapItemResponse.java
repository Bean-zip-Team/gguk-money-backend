package com.ggukmoney.beanzip.domain.keycap.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

@Schema(description = "내 보유 키캡 항목")
public record MyKeycapItemResponse(
        @Schema(description = "키캡 ID", example = "11111111-1111-1111-1111-111111111111")
        UUID keycapId,
        @Schema(description = "키캡 코드", example = "BASIC_BEAN")
        String code,
        @Schema(description = "키캡 이름", example = "기본 콩")
        String name,
        @Schema(description = "레벨. 처음 얻으면 1, 중복으로 뽑을 때마다 +1. 상한 없음", example = "9")
        int level,
        @Schema(description = "[deprecated] 보유 키캡은 항상 COMPLETED", example = "COMPLETED")
        String status,
        @Schema(description = "현재 장착 여부", example = "false")
        boolean equipped,
        @Schema(description = "키캡 등급", example = "COMMON")
        String grade,
        @Schema(description = "획득 경로. BOX: 상시(뽑기), EVENT: 시즌 한정. 시즌 키캡은 카탈로그에 없으므로 여기 값으로 그린다", example = "BOX")
        String acquisitionType,
        @Schema(description = "이미지 URL", example = "https://example.com/keycap.png")
        String imageUrl,
        @Schema(description = "사운드 URL", example = "https://example.com/keycap.mp3")
        String soundUrl,
        @Schema(description = "현재 레벨의 효과. enabled=false이면 지급에는 적용하지 않습니다.")
        KeycapPassiveEffectResponse effects
) {
    public MyKeycapItemResponse(UUID id,String code,String name,int level,String status,boolean equipped,
            String grade,String acquisitionType,String imageUrl,String soundUrl) {
        this(id,code,name,level,status,equipped,grade,acquisitionType,imageUrl,soundUrl,null);
    }
    public MyKeycapItemResponse withEffects(KeycapPassiveEffectResponse value) {
        return new MyKeycapItemResponse(keycapId,code,name,level,status,equipped,grade,acquisitionType,imageUrl,soundUrl,value);
    }
}
