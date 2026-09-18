package com.ggukmoney.beanzip.domain.ranking.boost;

import com.ggukmoney.beanzip.domain.ranking.entity.RankingSeason;
import org.springframework.stereotype.Component;
import java.time.Duration;
import java.time.Instant;

@Component
public class SystemRankingBoostRolloutGate {
    public boolean permits(Instant now, RankingSeason season) {
        // TODO(BEA-296): open ONLY after reward candidates consume historical exclusions,
        // and outsideCloseWindow is wired to the confirmed reward snapshot cutoff + guard duration.
        // This cannot be overridden by app_config or environment variables.
        return false;
    }

    public static boolean outsideCloseWindow(RankingSeason season, Instant now, Duration guard) {
        if (guard.isNegative()) throw new IllegalArgumentException("guard must not be negative");
        return season.getEndsAt() != null && season.contains(now)
                && now.isBefore(season.getEndsAt().minus(guard));
    }
}
