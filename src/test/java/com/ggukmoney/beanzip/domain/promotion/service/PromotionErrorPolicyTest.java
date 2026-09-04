package com.ggukmoney.beanzip.domain.promotion.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PromotionErrorPolicyTest {

    private final PromotionErrorPolicy policy = new PromotionErrorPolicy();

    @Test
    @DisplayName("4113 이미 지급은 에러가 아니라 멱등 성공이다")
    void alreadyGrantedConvergesToSuccess() {
        assertThat(policy.decide(PromotionErrorPolicy.ERROR_ALREADY_GRANTED))
                .isEqualTo(PromotionErrorPolicy.Decision.SUCCEED);
    }

    @Test
    @DisplayName("4112 머니 부족은 종료 실패가 아니라 대기다")
    void walletEmptyIsNotTerminal() {
        assertThat(policy.decide(PromotionErrorPolicy.ERROR_WALLET_EMPTY))
                .isEqualTo(PromotionErrorPolicy.Decision.WALLET_EMPTY);
    }

    @Test
    @DisplayName("종료·미실행 프로모션은 확정 실패다")
    void closedPromotionsFail() {
        assertThat(policy.decide(PromotionErrorPolicy.ERROR_PROMOTION_CLOSED))
                .isEqualTo(PromotionErrorPolicy.Decision.FAIL);
        assertThat(policy.decide(PromotionErrorPolicy.ERROR_PROMOTION_NOT_RUNNING))
                .isEqualTo(PromotionErrorPolicy.Decision.FAIL);
    }

    @Test
    @DisplayName("모르는 코드는 실패로 확정하지 않는다 — 수동 재지급 시 이중지급이 된다")
    void unknownCodeGoesToReview() {
        assertThat(policy.decide("9999")).isEqualTo(PromotionErrorPolicy.Decision.REVIEW);
        assertThat(policy.decide(null)).isEqualTo(PromotionErrorPolicy.Decision.REVIEW);
        assertThat(policy.decide("  ")).isEqualTo(PromotionErrorPolicy.Decision.REVIEW);
    }
}
