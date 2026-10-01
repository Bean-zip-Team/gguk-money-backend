package com.ggukmoney.beanzip.domain.mission.service;

import java.util.Optional;
import java.util.UUID;

/**
 * 데일리 미션 알림에 동의했는지 (BEA-299).
 *
 * <p>시트를 띄운 것만으로는 달성이 아니라 <b>동의를 완료해야</b> 달성이다. 수행 이력은 보상 행이
 * 대신 기억하므로, 나중에 알림을 꺼도 미션이 되살아나지 않는다.
 */
public interface NotificationOptInSignal {

    /**
     * 비어 있으면 <b>오늘은 판정하지 않는다</b>는 뜻이고, 미션이 목록에서 빠진다.
     *
     * <p>동의를 받을 수단 자체가 없는 상태가 여기 해당한다. 그때 미션을 그대로 보여 주면 유저는
     * 켤 방법이 없는 미션을 영원히 미완료로 보게 된다.
     */
    Optional<Boolean> agreed(UUID userId);
}
