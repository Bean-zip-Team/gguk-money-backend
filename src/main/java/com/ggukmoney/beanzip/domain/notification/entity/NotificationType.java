package com.ggukmoney.beanzip.domain.notification.entity;

public enum NotificationType {
    WEEKLY_REWARD_AVAILABLE,
    RANK_CHANGE,
    BOOSTER_RECHARGED,
    DAILY_REMINDER,

    /** 데일리 미션 저녁 리마인드. 비활동 유저를 부르는 DAILY_REMINDER 와 목적이 다르다(BEA-299). */
    DAILY_MISSION,
    BOOSTER_UNUSED,
    KEYCAP_BOX_OPEN_AVAILABLE
}
