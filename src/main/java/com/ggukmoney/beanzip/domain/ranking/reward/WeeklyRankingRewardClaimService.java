package com.ggukmoney.beanzip.domain.ranking.reward;

import com.ggukmoney.beanzip.domain.point.entity.PointAccount;
import com.ggukmoney.beanzip.domain.point.service.PointAccountService;
import com.ggukmoney.beanzip.domain.point.service.PointLedgerService;
import com.ggukmoney.beanzip.domain.ranking.boost.SystemRankingBoostPolicy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class WeeklyRankingRewardClaimService {

    private static final String POINT_REASON = "WEEKLY_RANKING_REWARD";

    private final WeeklyRankingRewardRepository rewardRepository;
    private final PointAccountService pointAccountService;
    private final PointLedgerService pointLedgerService;
    private final SystemRankingBoostPolicy internalAccountPolicy;
    private final Clock clock;

    @Transactional(noRollbackFor = ResponseStatusException.class)
    public WeeklyRankingReward claim(UUID userId, UUID rewardId) {
        WeeklyRankingReward reward = rewardRepository.findOwnedByPublicIdForUpdate(rewardId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "RANKING_REWARD_NOT_FOUND"));
        if (reward.getStatus() == WeeklyRankingReward.Status.CLAIMED) {
            return reward;
        }
        if (reward.getStatus() == WeeklyRankingReward.Status.EXPIRED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "RANKING_REWARD_EXPIRED");
        }

        Instant now = clock.instant();
        if (reward.expire(now)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "RANKING_REWARD_EXPIRED");
        }

        SystemRankingBoostPolicy.Snapshot internalAccountSnapshot = internalAccountPolicy.load(now)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.SERVICE_UNAVAILABLE,
                        "RANKING_REWARD_INTERNAL_ACCOUNT_POLICY_UNAVAILABLE"
                ));
        if (internalAccountSnapshot.internalUserIds().contains(userId)) {
            reward.claim(now);
            log.warn(
                    "WEEKLY_RANKING_REWARD_UNPAID reason=INTERNAL_ACCOUNT seasonId={} rewardRank={} userId={} rewardId={} pointAmount={}",
                    reward.getSeason().getId(),
                    reward.getRewardRank(),
                    userId,
                    reward.getPublicId(),
                    reward.getPointAmount()
            );
            return reward;
        }

        PointAccount account = pointAccountService.credit(userId, reward.getPointAmount());
        pointLedgerService.recordCredit(
                account,
                reward.getUser(),
                reward.getPointAmount(),
                POINT_REASON,
                reward.getPublicId()
        );
        reward.claim(now);
        return reward;
    }
}
