package com.ggukmoney.beanzip.domain.ranking.reward;

import com.ggukmoney.beanzip.domain.ranking.entity.RankingSeason;
import com.ggukmoney.beanzip.domain.ranking.entity.RankingSeasonStatus;
import com.ggukmoney.beanzip.domain.ranking.entity.RankingType;
import com.ggukmoney.beanzip.domain.ranking.repository.RankingSeasonRepository;
import com.ggukmoney.beanzip.domain.ranking.reward.dto.LatestWeeklyRankingRewardResponse;
import com.ggukmoney.beanzip.domain.ranking.reward.dto.MyWeeklyRankingRewardResponse;
import com.ggukmoney.beanzip.domain.ranking.reward.dto.MyWeeklyRankingRewardResponse.RewardStatus;
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
        WeeklyRankingReward myRewardRecord = rewards.stream()
                .filter(reward -> reward.getUser().getId().equals(userId))
                .findFirst()
                .orElse(null);
        MyWeeklyRankingRewardResponse myReward = myRewardRecord == null
                ? MyWeeklyRankingRewardResponse.none()
                : toMyReward(userId, myRewardRecord, now);

        return new LatestWeeklyRankingRewardResponse(
                new LatestWeeklyRankingRewardResponse.Season(
                        season.getCode(), season.getStartsAt(), season.getEndsAt()),
                winners,
                myReward,
                myRewardRecord != null && myRewardRecord.getViewedAt() == null
        );
    }

    public MyWeeklyRankingRewardResponse toMyReward(UUID userId, WeeklyRankingReward reward) {
        return toMyReward(userId, reward, clock.instant());
    }

    @Transactional
    public void markViewed(UUID userId, UUID rewardId) {
        WeeklyRankingReward reward = rewardRepository.findOwnedByPublicIdForUpdate(rewardId, userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "RANKING_REWARD_NOT_FOUND"));
        reward.markViewed(clock.instant());
    }

    private MyWeeklyRankingRewardResponse toMyReward(UUID userId, WeeklyRankingReward reward, Instant now) {
        RewardStatus claimStatus;
        if (reward.getStatus() == WeeklyRankingReward.Status.CLAIMED) {
            claimStatus = RewardStatus.CLAIMED;
        } else if (reward.getStatus() == WeeklyRankingReward.Status.EXPIRED || reward.isExpired(now)) {
            claimStatus = RewardStatus.EXPIRED;
        } else {
            claimStatus = RewardStatus.PENDING;
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
