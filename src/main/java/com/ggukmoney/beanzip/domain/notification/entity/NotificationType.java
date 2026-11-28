package com.ggukmoney.beanzip.domain.notification.entity;

public enum NotificationType {
    WEEKLY_REWARD_AVAILABLE,
    RANK_CHANGE,
    BOOSTER_RECHARGED,
    DAILY_REMINDER,

    /** 데일리 미션 저녁 리마인드. 비활동 유저를 부르는 DAILY_REMINDER 와 목적이 다르다(BEA-299). */
    DAILY_MISSION,
    BOOSTER_UNUSED,
    KEYCAP_BOX_OPEN_AVAILABLE,
    /**
     * 친구 알림 동의 (BEA-390). 알림 설정에 나오는 친구 타입은 이것 하나이고, 동의 한 번으로 아래 두
     * 푸시를 모두 받는다. 이 타입으로는 발송하지 않는다.
     */
    FRIEND,
    /** 응원 도착 푸시. 동의와 켜짐 여부는 {@link #FRIEND} 를 본다. */
    FRIEND_CHEER,
    /** 초대 성공·친구 연결 푸시. 동의와 켜짐 여부는 {@link #FRIEND} 를 본다. */
    FRIEND_INVITE_SUCCESS;

    /**
     * 사용자가 직접 동의하고 켜고 끄는 타입인지.
     *
     * <p>발송 전용 타입은 알림 설정에 나오지 않고 저장할 수도 없다. 저장을 받아 주면 켜 놓은 것처럼
     * 보이지만 발송은 {@link #FRIEND} 동의만 보므로 아무 효과가 없다.
     */
    public boolean hasOwnConsent() {
        return this != FRIEND_CHEER && this != FRIEND_INVITE_SUCCESS;
    }
}
