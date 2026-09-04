package com.ggukmoney.beanzip.domain.promotion.service;

import com.ggukmoney.beanzip.domain.promotion.entity.PromotionGrant;
import com.ggukmoney.beanzip.domain.promotion.repository.PromotionGrantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

/**
 * 상태 전이 전용. 트랜잭션을 짧게 유지하려고 외부 호출 오케스트레이션과 분리했다.
 * 모든 메서드가 자기 트랜잭션을 연다.
 */
@Service
@RequiredArgsConstructor
public class PromotionGrantStateService {

    private final PromotionGrantRepository promotionGrantRepository;

    @Transactional(readOnly = true)
    public Optional<PromotionGrant> find(Long grantId) {
        return promotionGrantRepository.findById(grantId);
    }

    /**
     * get-key 결과를 execute 이전에 확정한다.
     *
     * <p>환전과 반대 순서다. 환전은 execute 성공 뒤에 key 를 저장해서, 모호 실패가 나면 손에 쥔
     * key 를 버리고 자동 재시도를 포기했다. 우리는 자동 재시도가 필요하므로 key 를 먼저 커밋한다.
     * 같은 key 로 다시 부르면 토스가 4113(이미 지급)을 주고 그게 SUCCEEDED 로 수렴한다.
     *
     * <p>REQUIRES_NEW 인 이유는 호출자의 트랜잭션에 묶이면 "먼저 커밋"이 성립하지 않기 때문이다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void assignKey(Long grantId, String tossPromotionKey, String tossPromotionCode, Instant now) {
        promotionGrantRepository.findById(grantId)
                .ifPresent(grant -> grant.assignTossKey(tossPromotionKey, tossPromotionCode, now));
    }

    @Transactional
    public void markProcessing(Long grantId, Instant now) {
        promotionGrantRepository.findById(grantId).ifPresent(grant -> grant.markProcessing(now));
    }

    @Transactional
    public void markSucceeded(Long grantId, Instant now) {
        promotionGrantRepository.findById(grantId).ifPresent(grant -> grant.markSucceeded(now));
    }

    @Transactional
    public void markFailed(Long grantId, String tossErrorCode, String reason, Instant now) {
        promotionGrantRepository.findById(grantId)
                .ifPresent(grant -> grant.markFailed(tossErrorCode, reason, now));
    }

    @Transactional
    public void deferAttempt(Long grantId, Instant nextAttemptAt, String tossErrorCode, Instant now) {
        promotionGrantRepository.findById(grantId)
                .ifPresent(grant -> grant.deferAttempt(nextAttemptAt, tossErrorCode, now));
    }

    /** 설정 오류처럼 우리 쪽 문제. attempt 를 소모하지 않되 백오프는 걸어 무한 재시도를 막는다. */
    @Transactional
    public void deferWithoutAttempt(Long grantId, Instant nextAttemptAt, String tossErrorCode, Instant now) {
        promotionGrantRepository.findById(grantId)
                .ifPresent(grant -> grant.deferWithoutAttempt(nextAttemptAt, tossErrorCode, now));
    }

    @Transactional
    public void deferPoll(Long grantId, Instant nextAttemptAt, Instant now) {
        promotionGrantRepository.findById(grantId).ifPresent(grant -> grant.deferPoll(nextAttemptAt, now));
    }

    /** 사람이 지울 때까지 자동 진행에서 제외한다. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void hold(Long grantId, String holdReason, Instant now) {
        promotionGrantRepository.findById(grantId).ifPresent(grant -> {
            grant.hold(holdReason, now);
            grant.flagForReview(now);
        });
    }

    @Transactional
    public void flagForReview(Long grantId, Instant now) {
        promotionGrantRepository.findById(grantId).ifPresent(grant -> grant.flagForReview(now));
    }
}
