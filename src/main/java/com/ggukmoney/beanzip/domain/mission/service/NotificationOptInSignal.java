package com.ggukmoney.beanzip.domain.mission.service;

import java.util.UUID;

/**
 * 데일리 미션 알림에 동의했는지 (BEA-299).
 *
 * <p>시트를 띄운 것만으로는 달성이 아니라 <b>동의를 완료해야</b> 달성이다. 수행 이력은 보상 행이
 * 대신 기억하므로, 나중에 알림을 꺼도 미션이 되살아나지 않는다.
 */
public interface NotificationOptInSignal {

    boolean agreed(UUID userId);
}
