package com.ggukmoney.beanzip.domain.ranking.reward;

import com.ggukmoney.beanzip.domain.notification.entity.NotificationType;
import com.ggukmoney.beanzip.domain.notification.repository.NotificationPreferenceRepository;
import com.ggukmoney.beanzip.domain.ranking.entity.RankingSeason;
import com.ggukmoney.beanzip.domain.ranking.entity.RankingSeasonStatus;
import com.ggukmoney.beanzip.domain.ranking.entity.RankingType;
import com.ggukmoney.beanzip.domain.ranking.repository.RankingSeasonRepository;
import com.ggukmoney.beanzip.domain.ranking.reward.dto.LatestWeeklyRankingRewardResponse;
import com.ggukmoney.beanzip.domain.ranking.reward.dto.MyWeeklyRankingRewardResponse;
import com.ggukmoney.beanzip.domain.ranking.reward.dto.MyWeeklyRankingRewardResponse.ClaimStatus;
import com.ggukmoney.beanzip.global.util.NameMasker;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WeeklyRankingRewardQueryService {

    private final RankingSeasonRepository seasonRepository;
    private final WeeklyRankingRewardRepository rewardRepository;
    private final NotificationPreferenceRepository preferenceRepository;
    private final Clock clock;

    public LatestWeeklyRankingRewardResponse latest(UUID userId) {
        RankingSeason season = seasonRepository
                .findFirstByRankingTypeAndStatusOrderByEndsAtDesc(RankingType.WEEKLY, RankingSeasonStatus.CLOSED)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "RANKING_REWARD_RESULT_NOT_FOUND"));
        List<WeeklyRankingReward> rewards = rewardRepository.findAllWithUserBySeasonIdOrderByRewardRank(season.getId());
        Instant now = clock.instant();
        List<LatestWeeklyRankingRewardResponse.Winner> winners = rewards.stream()
                .map(reward -> toWinner(reward, userId))
                .toList();
        MyWeeklyRankingRewardResponse myReward = rewards.stream()
                .filter(reward -> reward.getUser().getId().equals(userId))
                .findFirst()
                .map(reward -> toMyReward(userId, reward, now))
                .orElse(null);

        return new LatestWeeklyRankingRewardResponse(
                new LatestWeeklyRankingRewardResponse.Season(
                        season.getCode(), season.getStartsAt(), season.getEndsAt()),
                winners,
                myReward
        );
    }

    public MyWeeklyRankingRewardResponse toMyReward(UUID userId, WeeklyRankingReward reward) {
        return toMyReward(userId, reward, clock.instant());
    }

    private MyWeeklyRankingRewardResponse toMyReward(UUID userId, WeeklyRankingReward reward, Instant now) {
        ClaimStatus claimStatus;
        if (reward.getStatus() == WeeklyRankingReward.Status.CLAIMED) {
            claimStatus = ClaimStatus.CLAIMED;
        } else if (reward.isExpired(now)) {
            claimStatus = ClaimStatus.EXPIRED;
        } else {
            boolean sendable = preferenceRepository.findByUserIdAndType(userId, NotificationType.RANK_CHANGE)
                    .map(preference -> preference.isSendable())
                    .orElse(false);
            claimStatus = sendable ? ClaimStatus.CLAIMABLE : ClaimStatus.CONSENT_REQUIRED;
        }
        return new MyWeeklyRankingRewardResponse(
                reward.getPublicId(),
                reward.getRewardRank(),
                reward.getPointAmount(),
                claimStatus,
                reward.getExpiresAt(),
                reward.getClaimedAt()
        );
    }

    private LatestWeeklyRankingRewardResponse.Winner toWinner(WeeklyRankingReward reward, UUID userId) {
        return new LatestWeeklyRankingRewardResponse.Winner(
                reward.getRewardRank(),
                reward.getSourceFinalRank(),
                reward.getUser().getId(),
                NameMasker.mask(reward.getUser().getNickname()),
                reward.getUser().getProfileImageUrl(),
                reward.getFinalScore(),
                reward.getPointAmount(),
                reward.getUser().getId().equals(userId)
        );
    }
}
