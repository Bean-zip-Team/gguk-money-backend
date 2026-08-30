package com.ggukmoney.beanzip.domain.promotion.scheduler;

import com.ggukmoney.beanzip.domain.promotion.entity.PromotionGrant;
import com.ggukmoney.beanzip.domain.promotion.repository.PromotionGrantRepository;
import com.ggukmoney.beanzip.domain.promotion.service.PromotionExecutionService;
import com.ggukmoney.beanzip.global.config.PromotionPolicyConfig;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * 유실·실패 건을 건져 최종 상태로 수렴시킨다.
 *
 * <p>Executor 가 빠른 경로라면 이쪽이 정확한 경로다. 블루/그린 전환은 JVM 을 죽이므로 큐에
 * 있던 작업은 정기적으로 증발한다. 그때 이 스케줄러가 유일한 복구 수단이다.
 *
 * <p>{@code CashoutProcessingScheduler} 와 구조는 같지만 그대로 베끼지 않았다. 그쪽은
 * BEA-250 에 풀스캔·무제한 조회·정지 수단 없음으로 성능 이슈가 걸려 있다. 여기서는
 * fixedDelay, 배치 상한, 부분 인덱스, SKIP LOCKED, 킬스위치를 처음부터 넣는다.
 */
@Component
@RequiredArgsConstructor
public class PromotionGrantRetryScheduler {

    private static final Logger log = LoggerFactory.getLogger(PromotionGrantRetryScheduler.class);

    private static final int BATCH_SIZE = 20;
    private static final Duration CLAIM_LEASE = Duration.ofMinutes(1);

    private final PromotionGrantRepository promotionGrantRepository;
    private final PromotionExecutionService promotionExecutionService;
    private final PromotionPolicyConfig policyConfig;
    private final Clock clock;

    @Scheduled(fixedDelay = 10_000, initialDelay = 30_000)
    public void processDueGrants() {
        boolean executionEnabled = policyConfig.executionEnabled();
        Instant now = Instant.now(clock);

        List<Long> claimed = executionEnabled ? claimDue(now) : List.of();

        int succeeded = 0;
        int failed = 0;
        boolean walletEmpty = false;
        for (Long grantId : claimed) {
            if (walletEmpty) {
                // 지갑이 비었으면 남은 execute 는 의미가 없다. 다만 tick 전체를 죽이지는 않는다.
                break;
            }
            try {
                walletEmpty = promotionExecutionService.execute(grantId);
                succeeded++;
            } catch (RuntimeException exception) {
                failed++;
                log.error("Promotion grant processing failed; grantId={}", grantId, exception);
            }
        }

        logSummary(executionEnabled, claimed.size(), succeeded, failed, walletEmpty, now);
    }

    /**
     * 처리 대상을 잠그고 임대 기간만큼 뒤로 밀어 다른 인스턴스가 같은 행을 집지 않게 한다.
     * 최후 방어선은 저장된 toss key 재사용이다.
     */
    @Transactional
    List<Long> claimDue(Instant now) {
        List<PromotionGrant> due = promotionGrantRepository.findDueForUpdate(now, PageRequest.of(0, BATCH_SIZE));
        Instant lease = now.plus(CLAIM_LEASE);
        return due.stream()
                .peek(grant -> grant.deferWithoutAttempt(lease, grant.getTossErrorCode(), now))
                .map(PromotionGrant::getId)
                .toList();
    }

    /**
     * tick 요약. 킬스위치 검사보다 <b>앞에서</b> 찍는다.
     *
     * <p>뒤에 두면 파이프라인이 멈춘 상태와 정상 상태가 똑같이 "로그 없음"으로 보인다.
     * 로그 부재를 알람 조건으로 걸면 배포 중 재기동마다 오탐이 나서 결국 알람을 끄게 된다.
     */
    private void logSummary(boolean executionEnabled, int claimed, int succeeded, int failed,
                            boolean walletEmpty, Instant now) {
        String promotionCode = PromotionPolicyConfig.KEYCAP_FIVE_PROMOTION_CODE;
        try {
            long needsReview = promotionGrantRepository.countByPromotionCodeAndNeedsReviewTrue(promotionCode);
            long held = promotionGrantRepository.countByPromotionCodeAndHoldReasonIsNotNull(promotionCode);
            long oldestSeconds = promotionGrantRepository.findOldestUnsettledCreatedAt(promotionCode)
                    .map(createdAt -> Duration.between(createdAt, now).toSeconds())
                    .orElse(0L);
            log.info("promotion tick: enabled={} exec={} claimed={} succeeded={} failed={} "
                            + "walletEmpty={} held={} needsReview={} oldestUnsettled={}s",
                    policyConfig.issuingEnabled(), executionEnabled, claimed, succeeded, failed,
                    walletEmpty, held, needsReview, oldestSeconds);
        } catch (RuntimeException exception) {
            log.warn("Failed to build promotion tick summary", exception);
        }
    }
}
