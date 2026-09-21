package com.ggukmoney.beanzip.domain.ranking.reward;

import com.ggukmoney.beanzip.domain.ranking.boost.RankingBoostRewardExclusions;
import com.ggukmoney.beanzip.domain.ranking.entity.RankingSeason;
import com.ggukmoney.beanzip.domain.ranking.repository.RankingEntryRepository;
import com.ggukmoney.beanzip.domain.user.entity.AppUser;
import com.ggukmoney.beanzip.domain.user.repository.AppUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class WeeklyRankingRewardSnapshotService {

    private final WeeklyRankingRewardPolicy policy;
    private final RankingBoostRewardExclusions exclusions;
    private final RankingEntryRepository entryRepository;
    private final AppUserRepository userRepository;
    private final WeeklyRankingRewardRepository rewardRepository;

    @Transactional(propagation = Propagation.MANDATORY)
    public int snapshot(RankingSeason season, Instant finalizedAt) {
        if (rewardRepository.existsBySeasonId(season.getId())) {
            return Math.toIntExact(rewardRepository.countBySeasonId(season.getId()));
        }

        WeeklyRankingRewardPolicy.Snapshot policySnapshot = policy.load(finalizedAt)
                .orElseThrow(() -> new IllegalStateException("weekly ranking reward policy unavailable"));
        if (!policySnapshot.enabled()) {
            log.info("Weekly ranking reward snapshot skipped seasonId={} enabled=false rewardCount=0",
                    season.getId());
            return 0;
        }

        Set<UUID> excludedUserIds = exclusions.excludedUserIds(season.getId(), finalizedAt)
                .orElseThrow(() -> new IllegalStateException("weekly ranking reward exclusions unavailable"));
        List<RankingEntryRepository.RankingRewardCandidateRow> candidates = entryRepository.findRewardCandidates(
                season.getId(), excludedUserIds, policySnapshot.maxRewardRank());
        Instant expiresAt = season.getEndsAt().plus(7, ChronoUnit.DAYS);

        Map<UUID, AppUser> usersById = userRepository.findAllById(
                        candidates.stream().map(RankingEntryRepository.RankingRewardCandidateRow::userId).toList())
                .stream()
                .collect(Collectors.toMap(AppUser::getId, Function.identity()));

        List<WeeklyRankingReward> rewards = new ArrayList<>(candidates.size());
        for (int index = 0; index < candidates.size(); index++) {
            RankingEntryRepository.RankingRewardCandidateRow candidate = candidates.get(index);
            AppUser user = usersById.get(candidate.userId());
            if (user == null) {
                throw new IllegalStateException("reward candidate user disappeared userId=" + candidate.userId());
            }
            int rewardRank = index + 1;
            rewards.add(WeeklyRankingReward.open(
                    season,
                    user,
                    candidate.sourceFinalRank(),
                    rewardRank,
                    candidate.finalScore(),
                    policySnapshot.pointAmount(rewardRank),
                    expiresAt
            ));
        }
        rewardRepository.saveAll(rewards);
        log.info("Weekly ranking rewards snapshotted seasonId={} enabled=true excludedCount={} rewardCount={}",
                season.getId(), excludedUserIds.size(), rewards.size());
        return rewards.size();
    }
}
