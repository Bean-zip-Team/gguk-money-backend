package com.ggukmoney.beanzip.domain.user.dto.response;

import com.ggukmoney.beanzip.domain.keycap.dto.response.EquippedKeycapResponse;

import java.util.UUID;

public record MemberMeResponse(
        UUID userId,
        String status,
        String nickname,
        String profileImageUrl,
        EquippedKeycapResponse equippedKeycap,
        long pointBalance
) {
}
