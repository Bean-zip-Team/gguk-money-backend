package com.ggukmoney.beanzip.domain.keycap.dto.response;
import com.ggukmoney.beanzip.domain.keycap.passive.KeycapPassivePolicy.*;
/** Nullable caps mean no effect; previewLevel distinguishes unowned Lv1 previews. */
public record KeycapPassiveEffectResponse(Critical shard,Critical click,Critical point,Integer capLevel,
        boolean capReached,int autoClicksPerDay,Integer autoClickCap,boolean autoClickCapReached,
        boolean enabled,Integer previewLevel) {
    public static KeycapPassiveEffectResponse from(Effects effect,boolean enabled,Integer previewLevel) {
        return new KeycapPassiveEffectResponse(effect.shard(),effect.click(),effect.point(),
                effect.capLevel()==0?null:effect.capLevel(),effect.capReached(),effect.autoClicksPerDay(),
                effect.autoClickCap()==0?null:effect.autoClickCap(),effect.autoClickCapReached(),enabled,previewLevel);
    }
}
