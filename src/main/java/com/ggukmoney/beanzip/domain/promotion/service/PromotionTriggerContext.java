package com.ggukmoney.beanzip.domain.promotion.service;

import com.ggukmoney.beanzip.domain.user.entity.AppUser;

import java.time.Instant;

/**
 * 자격 판정에 필요한 값 묶음. 트리거 구현이 리포지토리를 직접 뒤지지 않게 한다.
 *
 * <p>{@code triggerValue} 의 뜻은 미션마다 다르다. 발급기가 트리거를 파라미터로 받으므로
 * 어느 미션의 값인지는 호출부에서 이미 정해져 있다.
 *
 * @param user         판정 대상
 * @param triggerValue 미션별 판정값 — 키캡은 완성 수, 탭은 커트오프 이후 순증 탭 수
 * @param occurredAt   판정이 일어난 시각
 */
public record PromotionTriggerContext(
        AppUser user,
        long triggerValue,
        Instant occurredAt
) {

    /** 완성 키캡 수 (커트오프 미적용 — 트리거가 커트오프를 적용해 다시 센다). */
    public static PromotionTriggerContext keycapCompleted(AppUser user, long completedKeycapCount, Instant occurredAt) {
        return new PromotionTriggerContext(user, completedKeycapCount, occurredAt);
    }

    /** 커트오프 이후 순증 탭 수 = 누적 - 기준값. 트리거는 이 값만 보고 추가 조회를 하지 않는다. */
    public static PromotionTriggerContext tapThresholdCrossed(AppUser user, long netTapCount, Instant occurredAt) {
        return new PromotionTriggerContext(user, netTapCount, occurredAt);
    }
}
