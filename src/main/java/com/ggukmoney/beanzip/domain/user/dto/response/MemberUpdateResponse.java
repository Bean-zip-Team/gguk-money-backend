package com.ggukmoney.beanzip.domain.user.dto.response;

import java.util.UUID;

public record MemberUpdateResponse(
        UUID userId,
        String nickname,
        String profileImageUrl
) {
}
