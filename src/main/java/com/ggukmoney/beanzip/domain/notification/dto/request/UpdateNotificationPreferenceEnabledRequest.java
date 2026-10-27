package com.ggukmoney.beanzip.domain.notification.dto.request;

import com.ggukmoney.beanzip.domain.notification.entity.NotificationType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "알림 활성화 상태 변경 요청이에요.")
public record UpdateNotificationPreferenceEnabledRequest(
        @Schema(description = "활성화 상태를 바꿀 알림 타입이에요. 값별 의미는 알림 설정 응답의 `type` 필드 설명과 같아요.", example = "RANK_CHANGE")
        @NotNull NotificationType type,
        @Schema(description = "알림을 켤지 여부예요. true로 켜려면 해당 타입의 동의 상태가 `AGREED`여야 해요. 끌 때는 동의 상태와 상관없어요.", example = "false")
        boolean enabled
) {
}
