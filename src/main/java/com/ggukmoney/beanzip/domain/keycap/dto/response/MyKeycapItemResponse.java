package com.ggukmoney.beanzip.domain.keycap.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

@Schema(description = "내 보유 키캡 항목이에요.")
public record MyKeycapItemResponse(
        @Schema(description = "키캡 ID예요.", example = "11111111-1111-1111-1111-111111111111")
        UUID keycapId,
        @Schema(description = "키캡 코드예요.", example = "BASIC_BEAN")
        String code,
        @Schema(description = "키캡 이름이에요.", example = "기본 콩")
        String name,
        @Schema(description = "키캡 레벨이에요. 처음 얻으면 1이고, 같은 키캡을 다시 뽑을 때마다 1씩 올라요. 상한은 없어요.", example = "9")
        int level,
        @Schema(description = "[deprecated] 보유 키캡은 항상 `COMPLETED`예요.", example = "COMPLETED", deprecated = true)
        String status,
        @Schema(description = "현재 장착 중인지 여부예요.", example = "false")
        boolean equipped,
        @Schema(description = "키캡 등급이에요. `COMMON`, `RARE`, `EPIC`, `LEGENDARY` 중 하나예요.", example = "COMMON")
        String grade,
        @Schema(description = "획득 경로예요. `BOX`는 상시 뽑기, `EVENT`는 시즌 한정 지급이에요. 시즌 키캡은 카탈로그에 없으므로 이 값으로 구분해요.", example = "BOX")
        String acquisitionType,
        @Schema(description = "이미지 URL이에요.", example = "https://example.com/keycap.png")
        String imageUrl,
        @Schema(description = "사운드 URL이에요.", example = "https://example.com/keycap.mp3")
        String soundUrl,
        @Schema(description = "현재 레벨에서 계산한 효과예요. 실제 지급은 단일 장착 슬롯의 키캡에만 적용해요. `enabled=false`이면 신규 적립과 직접 탭 효과를 적용하지 않지만, 비활성화 전에 쌓인 자동 클릭은 다음 정산 때 지급해요.")
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
