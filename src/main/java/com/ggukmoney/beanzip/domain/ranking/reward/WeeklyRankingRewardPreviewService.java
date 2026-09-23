package com.ggukmoney.beanzip.domain.ranking.reward;

import com.ggukmoney.beanzip.domain.ranking.dto.response.RankingRewardTierResponse;
import com.ggukmoney.beanzip.domain.ranking.entity.RankingSeason;
import com.ggukmoney.beanzip.domain.ranking.repository.RankingEntryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WeeklyRankingRewardPreviewService {

    private final WeeklyRankingRewardPolicy policy;
    private final RankingEntryRepository entryRepository;

    public Preview preview(RankingSeason season, UUID userId, long myScore, java.time.Instant now) {
        Optional<WeeklyRankingRewardPolicy.Snapshot> loadedPolicy = policy.load(now);
        if (loadedPolicy.isEmpty() || !loadedPolicy.get().enabled()) {
            return Preview.unavailable();
        }
        WeeklyRankingRewardPolicy.Snapshot policySnapshot = loadedPolicy.get();
        List<RankingRewardTierResponse> tiers = policySnapshot.rewards().entrySet().stream()
                .map(entry -> new RankingRewardTierResponse(entry.getKey(), entry.getValue()))
                .toList();
        List<RankingEntryRepository.RankingCurrentRewardCandidateRow> candidates = entryRepository.findCurrentRewardCandidates(
                season.getId(), Set.of(), policySnapshot.maxRewardRank());

        Map<UUID, ProvisionalReward> rewardsByUser = new HashMap<>();
        for (int index = 0; index < candidates.size(); index++) {
            RankingEntryRepository.RankingCurrentRewardCandidateRow candidate = candidates.get(index);
            int rewardRank = index + 1;
            rewardsByUser.put(candidate.userId(), new ProvisionalReward(
                    rewardRank, policySnapshot.pointAmount(rewardRank)));
        }

        Long scoreGap = scoreGap(candidates, rewardsByUser.containsKey(userId), userId, myScore,
                policySnapshot.maxRewardRank());
        return new Preview(tiers, rewardsByUser, scoreGap, candidates);
    }

    private long scoreGap(
            List<RankingEntryRepository.RankingCurrentRewardCandidateRow> candidates,
            boolean alreadyRewardEligible,
            UUID userId,
            long myScore,
            int rewardCount
    ) {
        if (alreadyRewardEligible) {
            return 0L;
        }
        if (candidates.size() < rewardCount) {
            return myScore > 0 ? 0L : 1L;
        }

        RankingEntryRepository.RankingCurrentRewardCandidateRow cutoff = candidates.get(candidates.size() - 1);
        long requiredScore = cutoff.score();
        if (userId.toString().compareTo(cutoff.userId().toString()) <= 0) {
            if (requiredScore == Long.MAX_VALUE) {
                return Long.MAX_VALUE;
            }
            requiredScore++;
        }
        return Math.max(requiredScore - Math.max(myScore, 0L), 0L);
    }

    public record ProvisionalReward(int rewardRank, long pointAmount) {
    }

    public record Preview(
            List<RankingRewardTierResponse> tiers,
            Map<UUID, ProvisionalReward> rewardsByUser,
            Long scoreGapToReward,
            List<RankingEntryRepository.RankingCurrentRewardCandidateRow> candidateRows
    ) {
        public Preview {
            tiers = List.copyOf(tiers);
            rewardsByUser = Collections.unmodifiableMap(new HashMap<>(rewardsByUser));
            candidateRows = List.copyOf(candidateRows);
        }

        public Preview(
                List<RankingRewardTierResponse> tiers,
                Map<UUID, ProvisionalReward> rewardsByUser,
                Long scoreGapToReward
        ) {
            this(tiers, rewardsByUser, scoreGapToReward, List.of());
        }

        static Preview unavailable() {
            return new Preview(List.of(), Map.of(), null, List.of());
        }
    }
}
