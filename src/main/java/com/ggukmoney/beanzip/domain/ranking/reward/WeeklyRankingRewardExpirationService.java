package com.ggukmoney.beanzip.domain.ranking.reward;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class WeeklyRankingRewardExpirationService {

    private final WeeklyRankingRewardRepository rewardRepository;

    @Transactional
    public int expireDue(Instant now) {
        List<WeeklyRankingReward> expired = rewardRepository.findExpiredPendingForUpdate(now);
        long expiredAmount = 0L;
        int expiredCount = 0;
        for (WeeklyRankingReward reward : expired) {
            if (reward.expire(now)) {
                expiredCount++;
                expiredAmount += reward.getPointAmount();
            }
        }
        if (expiredCount > 0) {
            log.info("Weekly ranking rewards expired count={} amount={}", expiredCount, expiredAmount);
        }
        return expiredCount;
    }
}
