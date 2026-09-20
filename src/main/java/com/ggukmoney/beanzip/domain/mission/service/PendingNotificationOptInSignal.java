package com.ggukmoney.beanzip.domain.mission.service;

import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * 데일리 미션 알림 타입이 아직 없으므로 항상 미달성으로 본다.
 *
 * <p>미션 자체는 목록에 보여야 한다 — 이 미션의 목적이 동의를 받는 것이기 때문이다. BEA-299
 * 5단계에서 {@code DAILY_MISSION} 타입 동의 여부를 읽는 구현으로 교체한다.
 */
@Component
public class PendingNotificationOptInSignal implements NotificationOptInSignal {

    @Override
    public boolean agreed(UUID userId) {
        return false;
    }
}
