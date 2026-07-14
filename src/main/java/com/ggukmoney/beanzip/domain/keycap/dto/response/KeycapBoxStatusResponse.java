package com.ggukmoney.beanzip.domain.keycap.dto.response;

public record KeycapBoxStatusResponse(
        int boxBalance,
        int freeOpenTicketCount,
        long boxProgressTapCount,
        int nextBoxRequiredTapCount
) {
}
