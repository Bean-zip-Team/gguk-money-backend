package com.ggukmoney.beanzip.domain.notification.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "알림 설정 목록 응답이에요.")
public record NotificationPreferenceListResponse(
        @Schema(description = "알림 타입별 설정 목록이에요. Apps-in-Toss 캠페인 코드가 설정된 타입만 담아요.")
        List<NotificationPreferenceResponse> items
) {
}
