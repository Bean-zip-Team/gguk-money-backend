package com.ggukmoney.beanzip.domain.keycap.dto.response;

import com.ggukmoney.beanzip.domain.keycap.passive.KeycapPassivePolicy.Critical;
import com.ggukmoney.beanzip.domain.keycap.passive.KeycapPassivePolicy.Effects;
import io.swagger.v3.oas.annotations.media.Schema;

/** Nullable caps mean no effect; previewLevel distinguishes unowned Lv1 previews. */
@Schema(description = "키캡 레벨에서 계산한 패시브 효과예요. 크리티컬의 `probability`는 0~1 사이 확률이고 `multiplier`는 발동 배수예요. 레벨은 계속 오르며 효과만 각 축의 상한에서 멈춰요.")
public record KeycapPassiveEffectResponse(
        @Schema(description = "조각 드롭에 적용하는 크리티컬(①)의 확률과 배수예요. 해당 효과가 없으면 확률 0, 배수 1이에요.")
        Critical shard,
        @Schema(description = "직접 탭에 적용하는 클릭 크리티컬(②)의 확률과 배수예요. 자동 클릭에는 다시 적용하지 않아요.")
        Critical click,
        @Schema(description = "포인트 지급에 적용하는 크리티컬(③)의 확률과 배수예요. 배수를 적용한 뒤 일일 포인트 상한으로 잘라요.")
        Critical point,
        @Schema(description = "크리티컬 효과(①②③)의 등급별 캡 레벨이에요. 패시브 프로필이 없으면 null이에요. 자동 클릭 캡과는 별개예요.", nullable = true, example = "50")
        Integer capLevel,
        @Schema(description = "현재 레벨이 크리티컬 효과의 캡 레벨에 도달했는지 여부예요.", example = "false")
        boolean capReached,
        @Schema(description = "현재 레벨에서 장착 중 하루에 적립하는 자동 클릭(⑤) 수예요. 해당 효과가 없으면 0이에요.", example = "60")
        int autoClicksPerDay,
        @Schema(description = "레벨 상승으로 늘어나는 하루 자동 클릭 수의 상한이에요. 미수령 적립 상한과는 달라요. 자동 클릭 효과가 없으면 null이에요.", nullable = true, example = "180")
        Integer autoClickCap,
        @Schema(description = "현재 하루 자동 클릭 수가 레벨 성장 상한에 도달했는지 여부예요.", example = "false")
        boolean autoClickCapReached,
        @Schema(description = "신규 패시브 적립과 직접 탭 효과의 활성 여부예요. false여도 비활성화 전에 쌓인 자동 클릭은 다음 정산 때 지급해요.", example = "false")
        boolean enabled,
        @Schema(description = "미보유 카드 미리보기의 기준 레벨이에요. 미리보기는 1이며 보유 키캡의 실제 효과 응답에서는 이 필드가 null이에요.", nullable = true, example = "1")
        Integer previewLevel,
        @Schema(description = "현재 레벨 효과로 조각, 포인트, 랭킹 점수가 평균 몇 % 더 쌓이는지예요. 조각은 조각 크리티컬, 포인트는 포인트 크리티컬, 랭킹은 클릭 크리티컬만 반영하고 자동 클릭은 세 값 모두 0이에요. 소수점 둘째 자리까지 반올림해요.")
        Bonuses bonuses
) {
    @Schema(description = "효과 기대 증가율(%)이에요. 효과가 없는 축은 0이에요.")
    public record Bonuses(
            @Schema(description = "조각 획득량 기대 증가율(%)이에요.", example = "15")
            double shardPercent,
            @Schema(description = "포인트 획득량 기대 증가율(%)이에요.", example = "0")
            double pointPercent,
            @Schema(description = "랭킹 점수 기대 증가율(%)이에요.", example = "9")
            double rankingPercent
    ) {
        static Bonuses from(Effects effect) {
            return new Bonuses(percent(effect.shard()), percent(effect.point()), percent(effect.click()));
        }

        private static double percent(Critical critical) {
            return Math.round((critical.expectedMultiplier() - 1) * 10_000) / 100.0;
        }
    }

    public static KeycapPassiveEffectResponse from(Effects effect,boolean enabled,Integer previewLevel) {
        return new KeycapPassiveEffectResponse(effect.shard(),effect.click(),effect.point(),
                effect.capLevel()==0?null:effect.capLevel(),effect.capReached(),effect.autoClicksPerDay(),
                effect.autoClickCap()==0?null:effect.autoClickCap(),effect.autoClickCapReached(),enabled,previewLevel,
                Bonuses.from(effect));
    }
}
