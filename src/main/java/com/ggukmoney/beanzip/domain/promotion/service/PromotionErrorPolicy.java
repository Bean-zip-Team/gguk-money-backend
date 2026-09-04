package com.ggukmoney.beanzip.domain.promotion.service;

import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 토스 errorCode 를 전이로 옮기는 표.
 *
 * <p>코드 숫자값은 저장소 어디에서도 검증할 수 없다. 토스 문서와 콘솔에서 재확인해야 하고,
 * 틀렸을 때 여기 한 줄만 고치면 되도록 표로 둔다.
 *
 * <p>모르는 코드는 FAILED 가 아니라 REVIEW 다. 돈이 나갔는지 모르는 상태를 실패로 확정하면
 * 나중에 수동 재지급할 때 이중지급이 된다.
 */
@Component
public class PromotionErrorPolicy {

    /** 이미 지급되었거나 회수된 내역. 멱등이 동작한 것이므로 성공으로 수렴시킨다. */
    public static final String ERROR_ALREADY_GRANTED = "4113";
    /** 프로모션 머니 부족. 유저 귀책이 아니고 시간이 지나면 해소된다. */
    public static final String ERROR_WALLET_EMPTY = "4112";
    /** 종료된 프로모션. */
    public static final String ERROR_PROMOTION_CLOSED = "4105";
    /** 실행 중이 아닌 프로모션. */
    public static final String ERROR_PROMOTION_NOT_RUNNING = "4109";

    private static final Map<String, Decision> DECISIONS = Map.of(
            ERROR_ALREADY_GRANTED, Decision.SUCCEED,
            ERROR_WALLET_EMPTY, Decision.WALLET_EMPTY,
            ERROR_PROMOTION_CLOSED, Decision.FAIL,
            ERROR_PROMOTION_NOT_RUNNING, Decision.FAIL
    );

    public Decision decide(String tossErrorCode) {
        if (tossErrorCode == null || tossErrorCode.isBlank()) {
            return Decision.REVIEW;
        }
        return DECISIONS.getOrDefault(tossErrorCode.trim(), Decision.REVIEW);
    }

    public enum Decision {
        /** 성공으로 수렴 */
        SUCCEED,
        /** 종료 실패 */
        FAIL,
        /** 지갑이 빌 때까지 대기. execute 중단 신호이기도 하다. */
        WALLET_EMPTY,
        /** 확정하지 않고 사람이 본다 */
        REVIEW
    }
}
