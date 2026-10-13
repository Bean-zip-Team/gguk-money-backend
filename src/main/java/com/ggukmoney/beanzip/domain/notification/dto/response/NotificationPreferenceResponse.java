package com.ggukmoney.beanzip.domain.notification.dto.response;

import com.ggukmoney.beanzip.domain.notification.entity.NotificationAgreementStatus;
import com.ggukmoney.beanzip.domain.notification.entity.NotificationType;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "알림 설정 응답이에요.")
public record NotificationPreferenceResponse(
        @Schema(description = "알림 타입이에요. "
                + "`WEEKLY_REWARD_AVAILABLE`은 주간 랭킹 보상 알림 타입이에요. 지금은 주간 보상 알림을 이 타입이 아니라 `RANK_CHANGE` 동의로 보내요. "
                + "`RANK_CHANGE`는 순위 변동 알림이에요. 주간 랭킹 리셋 알림도 이 동의로 보내요. "
                + "`BOOSTER_RECHARGED`는 전날 부스터를 모두 쓴 사용자에게 아침에 보내는 부스터 충전 알림이에요. "
                + "`DAILY_REMINDER`는 비활동 사용자를 부르는 아침 리마인드예요. "
                + "`DAILY_MISSION`은 데일리 미션 저녁 리마인드예요(BEA-299). "
                + "`BOOSTER_UNUSED`는 부스터를 다 쓰지 않은 사용자에게 저녁에 보내는 알림이에요. "
                + "`KEYCAP_BOX_OPEN_AVAILABLE`은 키캡 상자 개봉 가능 알림이에요.",
                example = "RANK_CHANGE")
        NotificationType type,
        @Schema(description = "알림이 켜져 있는지 여부예요. 알림은 켜져 있고 동의 상태가 `AGREED`일 때만 보내요. 설정 기록이 없으면 false예요.", example = "true")
        boolean enabled,
        @Schema(description = "동의 상태예요. `NOT_REQUESTED`는 아직 동의를 요청하지 않음, `AGREED`는 동의함, `REJECTED`는 거절함이에요.", example = "AGREED")
        NotificationAgreementStatus agreementStatus,
        @Schema(description = "Apps-in-Toss 스마트 메시지 캠페인 코드예요. `requestNotificationAgreement` 호출에 이 값을 써요. 이 타입에 캠페인 코드가 설정되지 않았으면 null이에요.", example = "clickmoney-asfasf")
        String templateCode,
        @Schema(description = "알림 동의 요청을 띄워도 되는지 여부예요. `RANK_CHANGE` 타입이고 진행 중인 주간 랭킹에 참가 중일 때만 true예요. 다른 타입은 항상 false예요.", example = "true")
        boolean promptEligible
) {
}
