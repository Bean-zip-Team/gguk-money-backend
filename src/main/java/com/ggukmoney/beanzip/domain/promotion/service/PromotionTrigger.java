package com.ggukmoney.beanzip.domain.promotion.service;

import java.util.Optional;

/**
 * 자격 판정. 지급 파이프라인과 분리해 두는 유일한 이유는 교체 가능성이다.
 *
 * <p>앱인토스 검수 기준에 "확률형·게임 결과 기반 요소 미결합"이 있어, 키캡 랜덤 뽑기가 걸리면
 * 조건이 "상자 100번 열기" 같은 결정적 조건으로 바뀔 수 있다. 그때 갈아끼울 곳이 여기 하나여야
 * 한다. 파이프라인 본체는 "누가 자격을 얻었다"는 신호만 받으면 조건이 무엇이든 그대로 돈다.
 *
 * <p>구현체는 판정만 한다. 지급도, 부수효과도 없다.
 */
public interface PromotionTrigger {

    /** 이 트리거가 발급하는 논리 프로모션 코드. */
    String promotionCode();

    /**
     * 이 트리거가 지급하는 금액(토스 포인트).
     *
     * <p>프로모션마다 다르므로 트리거가 알려준다. 발급기가 단일 설정을 읽으면 미션이 둘이 되는
     * 순간 엉뚱한 금액이 나간다 — 5P 자리에 500P 가 들어가도 에러는 나지 않는다.
     */
    long amount();

    /**
     * 이 미션의 발급 스위치. 미션마다 따로 켜고 끈다 — 하나를 켜면 다른 하나까지 켜지면 안 된다.
     */
    boolean issuingEnabled();

    /**
     * 자격을 얻었으면 감사용 스냅샷을 담아 돌려준다. 아니면 {@link Optional#empty()}.
     *
     * <p>판정에 필요한 설정을 읽지 못한 경우에도 empty 다(fail-closed).
     */
    Optional<Integer> evaluate(PromotionTriggerContext context);
}
