package com.ggukmoney.beanzip.domain.ranking.reward;

import com.ggukmoney.beanzip.domain.notification.entity.NotificationType;
import com.ggukmoney.beanzip.domain.notification.repository.NotificationPreferenceRepository;
import com.ggukmoney.beanzip.domain.point.entity.PointAccount;
import com.ggukmoney.beanzip.domain.point.service.PointAccountService;
import com.ggukmoney.beanzip.domain.point.service.PointLedgerService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WeeklyRankingRewardClaimService {

    private static final String POINT_REASON = "WEEKLY_RANKING_REWARD";

    private final WeeklyRankingRewardRepository rewardRepository;
    private final NotificationPreferenceRepository preferenceRepository;
    private final PointAccountService pointAccountService;
    private final PointLedgerService pointLedgerService;
    private final Clock clock;

    @Transactional
    public WeeklyRankingReward claim(UUID userId, UUID rewardId) {
        WeeklyRankingReward reward = rewardRepository.findOwnedByPublicIdForUpdate(rewardId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "RANKING_REWARD_NOT_FOUND"));
        if (reward.getStatus() == WeeklyRankingReward.Status.CLAIMED) {
            return reward;
        }

        Instant now = clock.instant();
        if (reward.isExpired(now)) {
            throw new ResponseStatusException(HttpStatus.GONE, "RANKING_REWARD_EXPIRED");
        }
        boolean sendable = preferenceRepository.findByUserIdAndType(userId, NotificationType.RANK_CHANGE)
                .map(preference -> preference.isSendable())
                .orElse(false);
        if (!sendable) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "RANKING_REWARD_CONSENT_REQUIRED");
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
