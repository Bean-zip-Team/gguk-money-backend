package com.ggukmoney.beanzip.domain.ranking.boost;

import com.ggukmoney.beanzip.domain.ranking.entity.RankingSeason;
import org.springframework.stereotype.Component;
import java.time.Duration;
import java.time.Instant;

@Component
public class SystemRankingBoostRolloutGate {
    public boolean permits(Instant now, RankingSeason season) {
        // 마감(월 00:00 KST) 6시간 전은 일요일 18:00 이다. 부스트 창이 18~22시이므로 일요일은 통째로 빠진다.
        // 마감 직전에 실유저 1등을 추월하면 되찾을 시간 없이 상금이 사라지므로, 마지막 부스트는 토요일이다.
        return outsideCloseWindow(season, now, Duration.ofHours(6));
    }

    public static boolean outsideCloseWindow(RankingSeason season, Instant now, Duration guard) {
        if (guard.isNegative()) throw new IllegalArgumentException("guard must not be negative");
        return season.getEndsAt() != null && season.contains(now)
                && now.isBefore(season.getEndsAt().minus(guard));
    }
}
