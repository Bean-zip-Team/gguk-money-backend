package com.ggukmoney.beanzip.domain.keycap.passive;

import com.ggukmoney.beanzip.domain.keycap.passive.KeycapPassivePolicy.Critical;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class ClickCriticalScheduleTest {
    private final ClickCriticalSchedule schedule = new ClickCriticalSchedule("integration-test-secret-at-least-32-bytes-long");
    private final UUID user = UUID.fromString("6f1c2a4e-9b3d-4c57-8e21-0a5d7f9b3c18");
    private final Critical dolphin = new Critical(0.06, 2);

    @Test
    void appliesExactlyTheAnnouncedTapsAndKeepsOnlyTheEligiblePrefixForPoints() {
        var announced = schedule.upcoming(user, 0, dolphin);
        long inBatch = announced.stream().filter(offset -> offset <= 100).count();
        long inPrefix = announced.stream().filter(offset -> offset <= 60).count();

        var result = schedule.roll(user, 0, dolphin, 100, 60);

        assertThat(result.criticalCount()).isEqualTo(inBatch);
        assertThat(result.effectiveCount()).isEqualTo(100 + inBatch);
        assertThat(result.pointEligibleEffectiveCount()).isEqualTo(60 + inPrefix);
    }

    @Test
    void eachAnnouncedOffsetIsExactlyTheTapThatRollApplies() {
        var pudding = new Critical(0.13, 2);
        long base = 1_234;
        var announced = schedule.upcoming(user, base, pudding);
        for (int k = 1; k <= ClickCriticalSchedule.WINDOW; k++) {
            assertThat(schedule.roll(user, base + k - 1, pudding, 1, 1).criticalCount())
                    .as("tap %d after %d", k, base).isEqualTo(announced.contains(k) ? 1 : 0);
        }
        assertThat(announced).isNotEmpty();
    }

    @Test
    void laterAnnouncementsAgreeOnTheTapsTheyShare() {
        var first = schedule.upcoming(user, 0, dolphin);
        var later = schedule.upcoming(user, 100, dolphin);

        assertThat(later.stream().filter(offset -> offset <= 200).toList())
                .isEqualTo(first.stream().filter(offset -> offset > 100).map(offset -> offset - 100).toList());
        assertThat(later).allSatisfy(offset -> assertThat(offset).isBetween(1, ClickCriticalSchedule.WINDOW));
    }

    @Test
    void splittingABatchCannotChangeTheResult() {
        var whole = schedule.roll(user, 500, dolphin, 100, 73);
        int effective = 0, eligible = 0;
        for (int i = 0; i < 10; i++) {
            var part = schedule.roll(user, 500 + i * 10L, dolphin, 10, Math.clamp(73 - i * 10, 0, 10));
            effective += part.effectiveCount();
            eligible += part.pointEligibleEffectiveCount();
        }
        assertThat(effective).isEqualTo(whole.effectiveCount());
        assertThat(eligible).isEqualTo(whole.pointEligibleEffectiveCount());
    }

    @Test
    void aLowerProbabilityOnlyEverPicksTapsAHigherOneAlsoPicks() {
        // Re-equipping cannot re-roll: the draw ignores the keycap.
        var lucky = schedule.upcoming(user, 0, new Critical(0.015, 5));
        var pudding = schedule.upcoming(user, 0, new Critical(0.13, 2));
        assertThat(pudding).containsAll(lucky);
        assertThat(schedule.upcoming(user, 0, new Critical(0.015, 5))).isEqualTo(lucky);
    }

    @Test
    void hitsTheConfiguredProbabilityOverManyTaps() {
        int taps = 100_000;
        var result = schedule.roll(user, 0, dolphin, taps, taps);
        // 5 standard deviations: sqrt(100000 * 0.06 * 0.94) is about 75.
        assertThat(result.criticalCount()).isBetween(6_000 - 375, 6_000 + 375);
    }

    @Test
    void dependsOnTheServerKeyAndTheUser() {
        var other = new ClickCriticalSchedule("another-secret-that-is-at-least-32-bytes");
        var pudding = new Critical(0.13, 2);
        assertThat(other.upcoming(user, 0, pudding)).isNotEqualTo(schedule.upcoming(user, 0, pudding));
        assertThat(schedule.upcoming(UUID.randomUUID(), 0, pudding)).isNotEqualTo(schedule.upcoming(user, 0, pudding));
    }

    @Test
    void withoutTheEffectAnnouncesNothingAndAppliesRawCounts() {
        assertThat(schedule.upcoming(user, 0, Critical.NONE)).isNull();
        var result = schedule.roll(user, 0, Critical.NONE, 10, 6);
        assertThat(result).isEqualTo(new ClickCriticalSchedule.ClickResult(10, 10, 6, 0));
    }

    @Test
    void rejectsAnEligiblePrefixOutsideTheAcceptedRange() {
        assertThatThrownBy(() -> schedule.roll(user, 0, Critical.NONE, 2, 3)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> schedule.roll(user, 0, Critical.NONE, -1, 0)).isInstanceOf(IllegalArgumentException.class);
    }
}
