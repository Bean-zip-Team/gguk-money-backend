package com.ggukmoney.beanzip.domain.tap.dto.response;

public record TapBatchSubmitResponse(
        int acceptedCount,
        int pointsAwarded,
        long balance
) {
}
