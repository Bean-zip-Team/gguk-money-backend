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
    void rollsOncePerDropAndOncePerPayoutInsteadOfOncePerBatch() {
        var roller = new KeycapPassiveRoller(new SequenceRandom(0.01, 0.10), new SequenceRandom(0.01, 0.025));
        assertThat(roller.rollShard(policy.effects("main", 50), 1)).isEqualTo(2);
        assertThat(roller.rollShard(policy.effects("main", 50), 1)).isEqualTo(1);
        assertThat(roller.rollPoint(policy.effects("yellowlego", 50), 1, 150)).isEqualTo(5);
        assertThat(roller.rollPoint(policy.effects("yellowlego", 50), 1, 150)).isEqualTo(1);
    }

    @Test
    void clampsPointsAfterCriticalAndConsumesEvenACappedPayoutRoll() {
        var points = new SequenceRandom(0.01, 0.01, 0.5);
        var roller = new KeycapPassiveRoller(new SequenceRandom(), points);
        var effects = policy.effects("yellowlego", 50);
        assertThat(roller.rollPoint(effects, 1, 1)).isEqualTo(1);
        assertThat(roller.rollPoint(effects, 1, 0)).isZero();
        assertThat(roller.rollPoint(effects, 1, 150)).isEqualTo(1);
        assertThat(points.remaining()).isZero();
    }

    @Test
    void noEquipmentUsesRawCountsWithoutDrawingOrAddingAnotherBooster() {
        var roller = new KeycapPassiveRoller(new SequenceRandom(), new SequenceRandom());
        assertThat(roller.rollShard(policy.effects(null, 1), 1)).isEqualTo(1);
        assertThat(roller.rollPoint(policy.effects(null, 1), 1, 150)).isEqualTo(1);
    }

    @Test
    void keepsAxisRandomStreamsStableWhenRequestsAreSplit() {
        var whole = new KeycapPassiveRoller(new Random(43), new Random(44));
        var split = new KeycapPassiveRoller(new Random(43), new Random(44));
        var effects = policy.effects("pudding", 5);
        int wholeShards = 0;
        int wholePoints = 0;
        for (int i = 0; i < 10; i++) {
            wholeShards += whole.rollShard(effects, 1);
            wholePoints += whole.rollPoint(effects, 1, 150);
        }
        int shards = 0, points = 0;
        for (int i = 0; i < 10; i++) {
            shards += split.rollShard(effects, 1);
            points += split.rollPoint(effects, 1, 150);
        }
        assertThat(shards).isEqualTo(wholeShards);
        assertThat(points).isEqualTo(wholePoints);
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
