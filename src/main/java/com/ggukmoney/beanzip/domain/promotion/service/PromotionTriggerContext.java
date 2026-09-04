package com.ggukmoney.beanzip.domain.promotion.service;

import com.ggukmoney.beanzip.domain.user.entity.AppUser;

import java.time.Instant;

/**
 * 자격 판정에 필요한 값 묶음. 트리거 구현이 리포지토리를 직접 뒤지지 않게 한다.
 *
 * @param user                 판정 대상
 * @param completedKeycapCount 전체 완성 키캡 수 (커트오프 미적용)
 * @param occurredAt           판정이 일어난 시각
 */
public record PromotionTriggerContext(
        AppUser user,
        long completedKeycapCount,
        Instant occurredAt
) {

    public static PromotionTriggerContext keycapCompleted(AppUser user, long completedKeycapCount, Instant occurredAt) {
        return new PromotionTriggerContext(user, completedKeycapCount, occurredAt);
    }
}
