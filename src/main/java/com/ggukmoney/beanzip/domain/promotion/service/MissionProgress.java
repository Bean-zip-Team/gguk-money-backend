package com.ggukmoney.beanzip.domain.promotion.service;

/**
 * 미션 진행도 (BEA-292).
 *
 * <p>서버가 계산해서 내려준다. 앱이 임계치만 받아 계산하는 방식은 1,000번 누르기에서 성립하지
 * 않는다 — 진행도가 {@code 누적 탭 - 기준값} 인데 기준값이 서버에만 있기 때문이다.
 *
 * @param current 현재 값. 커트오프가 적용된 값이다.
 * @param target  달성에 필요한 값
 */
public record MissionProgress(long current, long target) {

    public static MissionProgress of(long current, long target) {
        return new MissionProgress(Math.max(current, 0L), target);
    }

    public boolean reached() {
        return current >= target;
    }
}
