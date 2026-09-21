package com.ggukmoney.beanzip.domain.ranking.boost;

import org.springframework.stereotype.Component;
import java.time.*;
import java.util.concurrent.ThreadLocalRandom;

@Component
public class RankingBoostRandom {
    public static final ZoneId KST = ZoneId.of("Asia/Seoul");

    public Instant scheduledAt(LocalDate date) {
        return date.atTime(18, 0).atZone(KST).plusMinutes(ThreadLocalRandom.current().nextInt(48) * 5L).toInstant();
    }

    public int increment(int min, int max) {
        return (int) ThreadLocalRandom.current().nextLong(min, (long) max + 1);
    }
}
