package com.ggukmoney.beanzip.domain.keycap.passive;

import org.junit.jupiter.api.Test;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Random;
import java.util.random.RandomGenerator;
import static org.assertj.core.api.Assertions.*;

class KeycapPassiveRollerTest {
    private final KeycapPassivePolicy policy = KeycapPassivePolicy.defaults();

    @Test
    void rollsEachRawTapAndKeepsOnlyTheEligiblePrefixForPoints() {
        var clicks = new SequenceRandom(0.01, 0.07, 0.059, 0.06);
        var roller = new KeycapPassiveRoller(clicks, new SequenceRandom(), new SequenceRandom());
        var result = roller.rollClicks(policy.effects("dolphin", 50), 4, 2);
        assertThat(result.acceptedCount()).isEqualTo(4);
        assertThat(result.effectiveCount()).isEqualTo(6);
        assertThat(result.pointEligibleEffectiveCount()).isEqualTo(3);
        assertThat(clicks.remaining()).isZero();
    }

    @Test
    void rollsOncePerDropAndOncePerPayoutInsteadOfOncePerBatch() {
        var roller = new KeycapPassiveRoller(new SequenceRandom(), new SequenceRandom(0.01, 0.10),
                new SequenceRandom(0.01, 0.025));
        assertThat(roller.rollShard(policy.effects("main", 50), 1)).isEqualTo(2);
        assertThat(roller.rollShard(policy.effects("main", 50), 1)).isEqualTo(1);
        assertThat(roller.rollPoint(policy.effects("yellowlego", 50), 1, 150)).isEqualTo(5);
        assertThat(roller.rollPoint(policy.effects("yellowlego", 50), 1, 150)).isEqualTo(1);
    }

    @Test
    void clampsPointsAfterCriticalAndConsumesEvenACappedPayoutRoll() {
        var points = new SequenceRandom(0.01, 0.01, 0.5);
        var roller = new KeycapPassiveRoller(new SequenceRandom(), new SequenceRandom(), points);
        var effects = policy.effects("yellowlego", 50);
        assertThat(roller.rollPoint(effects, 1, 1)).isEqualTo(1);
        assertThat(roller.rollPoint(effects, 1, 0)).isZero();
        assertThat(roller.rollPoint(effects, 1, 150)).isEqualTo(1);
        assertThat(points.remaining()).isZero();
    }

    @Test
    void noEquipmentUsesRawCountsWithoutDrawingOrAddingAnotherBooster() {
        var roller = new KeycapPassiveRoller(new SequenceRandom(), new SequenceRandom(), new SequenceRandom());
        var result = roller.rollClicks(policy.effects(null, 1), 10, 6);
        assertThat(result.acceptedCount()).isEqualTo(10);
        assertThat(result.effectiveCount()).isEqualTo(10);
        assertThat(result.pointEligibleEffectiveCount()).isEqualTo(6);
        assertThat(roller.rollShard(policy.effects(null, 1), 1)).isEqualTo(1);
        assertThat(roller.rollPoint(policy.effects(null, 1), 1, 150)).isEqualTo(1);
    }

    @Test
    void keepsAxisRandomStreamsStableWhenRequestsAreSplit() {
        var whole = new KeycapPassiveRoller(new Random(42), new Random(43), new Random(44));
        var split = new KeycapPassiveRoller(new Random(42), new Random(43), new Random(44));
        var effects = policy.effects("pudding", 5);
        var total = whole.rollClicks(effects, 100, 73);
        int wholeShards = 0;
        int wholePoints = 0;
        for (int i = 0; i < 10; i++) {
            wholeShards += whole.rollShard(effects, 1);
            wholePoints += whole.rollPoint(effects, 1, 150);
        }
        int effective = 0, eligible = 0, shards = 0, points = 0;
        for (int i = 0; i < 10; i++) {
            var part = split.rollClicks(effects, 10, Math.clamp(73 - i * 10, 0, 10));
            effective += part.effectiveCount();
            eligible += part.pointEligibleEffectiveCount();
            shards += split.rollShard(effects, 1);
            points += split.rollPoint(effects, 1, 150);
        }
        assertThat(effective).isEqualTo(total.effectiveCount());
        assertThat(eligible).isEqualTo(total.pointEligibleEffectiveCount());
        assertThat(shards).isEqualTo(wholeShards);
        assertThat(points).isEqualTo(wholePoints);
    }

    @Test
    void rejectsAnEligiblePrefixOutsideTheAcceptedRange() {
        var roller = new KeycapPassiveRoller(new Random(1), new Random(2), new Random(3));
        assertThatThrownBy(() -> roller.rollClicks(KeycapPassivePolicy.Effects.NONE, 2, 3))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> roller.rollClicks(KeycapPassivePolicy.Effects.NONE, -1, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static final class SequenceRandom implements RandomGenerator {
        private final ArrayDeque<Double> values = new ArrayDeque<>();
        SequenceRandom(double... values) { Arrays.stream(values).forEach(this.values::add); }
        @Override public long nextLong() { throw new AssertionError("Unexpected nextLong"); }
        @Override public double nextDouble() {
            if (values.isEmpty()) throw new AssertionError("Unexpected extra critical trial");
            return values.remove();
        }
        int remaining() { return values.size(); }
    }
}
