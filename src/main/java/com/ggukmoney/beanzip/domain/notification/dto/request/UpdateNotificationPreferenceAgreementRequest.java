package com.ggukmoney.beanzip.domain.notification.dto.request;

import com.ggukmoney.beanzip.domain.notification.entity.NotificationType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Schema(description = "알림 동의 결과 저장 요청이에요.")
public record UpdateNotificationPreferenceAgreementRequest(
        @Schema(description = "동의 결과를 저장할 알림 타입이에요. 값별 의미는 알림 설정 응답의 `type` 필드 설명과 같아요.", example = "RANK_CHANGE")
        @NotNull NotificationType type,
        @Schema(description = "Apps-in-Toss `requestNotificationAgreement` 호출 결과예요. "
                + "`newAgreement` 값과 `alreadyAgreed` 값은 동의로 저장하고 알림을 켜요. "
                + "`agreementRejected` 값은 거절로 저장하고 알림을 꺼요. "
                + "다른 값을 보내면 400을 돌려줘요.", example = "newAgreement")
        @NotBlank String agreementResult
) {
}
