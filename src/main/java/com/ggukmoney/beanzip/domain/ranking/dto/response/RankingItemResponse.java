package com.ggukmoney.beanzip.domain.ranking.dto.response;

import java.util.UUID;

public record RankingItemResponse(
        long rank,
        UUID userId,
        String nickname,
        String profileImageUrl,
        long score,
        boolean isMe
) {
}
