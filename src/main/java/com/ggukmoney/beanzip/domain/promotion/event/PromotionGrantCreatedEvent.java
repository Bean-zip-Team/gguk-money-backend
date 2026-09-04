package com.ggukmoney.beanzip.domain.promotion.event;

/**
 * 지급 대상 행이 생겼다는 신호. 엔티티가 아니라 id 만 담는다.
 * 커밋 이후 다른 스레드에서 처리되므로 엔티티를 실으면 detached 인스턴스를 만지게 된다.
 */
public record PromotionGrantCreatedEvent(Long grantId) {
}
