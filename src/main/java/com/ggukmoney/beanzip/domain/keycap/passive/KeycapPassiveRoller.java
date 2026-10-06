package com.ggukmoney.beanzip.domain.keycap.passive;

import com.ggukmoney.beanzip.domain.keycap.passive.KeycapPassivePolicy.Critical;
import com.ggukmoney.beanzip.domain.keycap.passive.KeycapPassivePolicy.Effects;

import java.util.Objects;
import java.util.random.RandomGenerator;

/**
 * Independent server trials for accepted taps, shard drops and point payout boundaries.
 * Results must be persisted by the transaction adapter before a request can be replayed.
 */
public final class KeycapPassiveRoller {
    private final RandomGenerator clickRandom;
    private final RandomGenerator shardRandom;
    private final RandomGenerator pointRandom;

    public KeycapPassiveRoller() {
        this(RandomGenerator.getDefault(), RandomGenerator.getDefault(), RandomGenerator.getDefault());
    }

    public KeycapPassiveRoller(RandomGenerator clickRandom, RandomGenerator shardRandom, RandomGenerator pointRandom) {
        this.clickRandom = Objects.requireNonNull(clickRandom);
        this.shardRandom = Objects.requireNonNull(shardRandom);
        this.pointRandom = Objects.requireNonNull(pointRandom);
    }

    public ClickResult rollClicks(Effects effects, int acceptedCount, int pointEligibleRawCount) {
        Objects.requireNonNull(effects);
        if (acceptedCount < 0 || pointEligibleRawCount < 0 || pointEligibleRawCount > acceptedCount) {
            throw new IllegalArgumentException("Invalid accepted tap prefix");
        }
        int effectiveCount = 0;
        int eligibleEffectiveCount = 0;
        for (int i = 0; i < acceptedCount; i++) {
            int multiplier = roll(effects.click(), clickRandom);
            effectiveCount = Math.addExact(effectiveCount, multiplier);
            if (i < pointEligibleRawCount) {
                eligibleEffectiveCount = Math.addExact(eligibleEffectiveCount, multiplier);
            }
        }
        return new ClickResult(acceptedCount, effectiveCount, eligibleEffectiveCount);
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

    private int roll(Critical effect, RandomGenerator random) {
        return effect.probability() > 0 && random.nextDouble() < effect.probability() ? effect.multiplier() : 1;
    }

    public record ClickResult(int acceptedCount, int effectiveCount, int pointEligibleEffectiveCount) {}
}
