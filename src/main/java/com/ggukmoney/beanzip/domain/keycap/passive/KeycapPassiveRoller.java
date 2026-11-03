package com.ggukmoney.beanzip.domain.keycap.passive;

import com.ggukmoney.beanzip.domain.keycap.passive.KeycapPassivePolicy.Critical;
import com.ggukmoney.beanzip.domain.keycap.passive.KeycapPassivePolicy.Effects;

import java.util.Objects;
import java.util.random.RandomGenerator;

/**
 * Independent server trials for shard drops and point payout boundaries.
 * Results must be persisted by the transaction adapter before a request can be replayed.
 * Click criticals are announced ahead of the taps, so {@link ClickCriticalSchedule} decides them.
 */
public final class KeycapPassiveRoller {
    private final RandomGenerator shardRandom;
    private final RandomGenerator pointRandom;

    public KeycapPassiveRoller() {
        this(RandomGenerator.getDefault(), RandomGenerator.getDefault());
    }

    public KeycapPassiveRoller(RandomGenerator shardRandom, RandomGenerator pointRandom) {
        this.shardRandom = Objects.requireNonNull(shardRandom);
        this.pointRandom = Objects.requireNonNull(pointRandom);
    }

    public int rollShard(Effects effects, int baseAmount) {
        if (baseAmount <= 0) {
            throw new IllegalArgumentException("Shard drop must be positive");
        }
        return Math.multiplyExact(baseAmount, roll(effects.shard(), shardRandom));
    }

    public int rollPoint(Effects effects, int baseAmount, int remainingDailyPoints) {
        if (baseAmount <= 0 || remainingDailyPoints < 0) {
            throw new IllegalArgumentException("Invalid point payout");
        }
        // A crossed boundary consumes its trial even if the daily cap leaves no credit.
        long candidate = (long) baseAmount * roll(effects.point(), pointRandom);
        return (int) Math.min(candidate, remainingDailyPoints);
    }

    /** BEA-360: one independent trial per unpaid tap; returns how many hit. Shares the point stream. */
    public int rollOverflowHits(int taps, double probability) {
        if (taps < 0 || probability < 0 || probability > 1) {
            throw new IllegalArgumentException("Invalid overflow trial");
        }
        int hits = 0;
        for (int i = 0; i < taps; i++) {
            if (probability > 0 && pointRandom.nextDouble() < probability) hits++;
        }
        return hits;
    }

    private int roll(Critical effect, RandomGenerator random) {
        return effect.probability() > 0 && random.nextDouble() < effect.probability() ? effect.multiplier() : 1;
    }
}
