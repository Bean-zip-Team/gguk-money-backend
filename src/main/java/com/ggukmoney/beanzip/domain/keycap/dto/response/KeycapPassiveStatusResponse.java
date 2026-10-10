package com.ggukmoney.beanzip.domain.keycap.dto.response;
import java.time.Instant;
public record KeycapPassiveStatusResponse(boolean enabled,String equippedKeycapCode,Integer equippedLevel,
        KeycapPassiveEffectResponse effects,int pendingAutoClicks,int autoClickAccrualCap,
        boolean capped,Instant lastSettledAt,Instant asOf) {}
