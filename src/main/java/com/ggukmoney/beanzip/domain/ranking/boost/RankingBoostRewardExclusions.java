package com.ggukmoney.beanzip.domain.ranking.boost;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.*;

/** BEA-296 must filter candidates BEFORE assigning reward ranks; no payout is implemented here. */
@Service
@RequiredArgsConstructor
public class RankingBoostRewardExclusions {
    private final SystemRankingBoostPolicy policy;
    private final RankingBoostRunRepository runs;

    @Transactional(readOnly = true)
    public Optional<Set<UUID>> excludedUserIds(Long seasonId, Instant now) {
        return policy.load(now).map(snapshot -> {
            Set<UUID> excluded = new HashSet<>(snapshot.internalUserIds());
            excluded.addAll(runs.findSeasonRecipients(seasonId));
            return Set.copyOf(excluded);
        });
    }
}
