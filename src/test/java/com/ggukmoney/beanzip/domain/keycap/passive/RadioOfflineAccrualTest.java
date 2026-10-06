package com.ggukmoney.beanzip.domain.keycap.passive;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.time.Duration;
import java.time.Instant;
import static org.assertj.core.api.Assertions.*;

class RadioOfflineAccrualTest {
    private static final Instant START = Instant.parse("2026-10-06T00:00:00Z");
    private final RadioOfflineAccrual calculator = new RadioOfflineAccrual();
    private final RadioOfflineAccrual.Policy policy = RadioOfflineAccrual.Policy.defaults(true, START);

    @Test
    void firstActivityInitializesWithoutRetroactiveCredit() {
        var now = START.plus(Duration.ofDays(30));
        var result = calculator.calculate(null, true, now, policy);
        assertThat(result.grantedShards()).isZero();
        assertThat(result.checkpoint()).isEqualTo(new RadioOfflineAccrual.Checkpoint(now, Duration.ZERO));
    }

    @ParameterizedTest
    @CsvSource({
        "1799,0,0,false", "1800,0,1800,false",
        "86399,0,86399,false", "86400,1,0,false",
        "604800,7,0,true", "2592000,7,0,true"
    })
    void respectsIdleDayAndCapBoundaries(long elapsedSeconds, int shards, long remainder, boolean capped) {
        var result = calculator.calculate(state(START, Duration.ZERO), true, START.plusSeconds(elapsedSeconds), policy);
        assertThat(result.grantedShards()).isEqualTo(shards);
        assertThat(result.checkpoint().remainder()).isEqualTo(Duration.ofSeconds(remainder));
        assertThat(result.capped()).isEqualTo(capped);
    }

    @Test
    void twoTwelveHourGapsPayOneShardWithoutLosingRemainder() {
        var first = calculator.calculate(state(START, Duration.ZERO), true, START.plus(Duration.ofHours(12)), policy);
        assertThat(first.grantedShards()).isZero();
        assertThat(first.checkpoint().remainder()).isEqualTo(Duration.ofHours(12));
        var second = calculator.calculate(first.checkpoint(), true, START.plus(Duration.ofDays(1)), policy);
        assertThat(second.grantedShards()).isEqualTo(1);
        assertThat(second.checkpoint().remainder()).isZero();
    }

    @Test
    void unequippedTimePausesButDoesNotEraseEarnedRemainder() {
        var paused = calculator.calculate(state(START, Duration.ofHours(12)), false,
                START.plus(Duration.ofDays(1)), policy);
        assertThat(paused.grantedShards()).isZero();
        assertThat(paused.checkpoint().remainder()).isEqualTo(Duration.ofHours(12));
        var resumed = calculator.calculate(paused.checkpoint(), true,
                START.plus(Duration.ofHours(36)), policy);
        assertThat(resumed.grantedShards()).isEqualTo(1);
    }

    @Test
    void discardsExcessTimeIncludingTheFractionBeyondSevenDays() {
        var result = calculator.calculate(state(START, Duration.ofHours(12)), true,
                START.plus(Duration.ofDays(30)), policy);
        assertThat(result.grantedShards()).isEqualTo(7);
        assertThat(result.checkpoint().remainder()).isZero();
        var next = calculator.calculate(result.checkpoint(), true,
                START.plus(Duration.ofDays(30)).plus(Duration.ofHours(1)), policy);
        assertThat(next.grantedShards()).isZero();
        assertThat(next.checkpoint().remainder()).isEqualTo(Duration.ofHours(1));
    }

    @Test
    void reactivationExcludesDisabledTimeEvenWhenThereWasNoRequestDuringIt() {
        var reactivated = RadioOfflineAccrual.Policy.defaults(true, START.plus(Duration.ofHours(36)));
        var result = calculator.calculate(state(START, Duration.ofHours(12)), true,
                START.plus(Duration.ofDays(2)), reactivated);
        assertThat(result.grantedShards()).isEqualTo(1);
        assertThat(result.checkpoint().remainder()).isZero();
    }

    @Test
    void idleClassificationUsesActivityGapAndAccrualUsesOnlyTheEnabledPortion() {
        var now = START.plus(Duration.ofHours(2));
        var recentlyEnabled = RadioOfflineAccrual.Policy.defaults(true, now.minus(Duration.ofMinutes(10)));
        var result = calculator.calculate(state(START, Duration.ZERO), true, now, recentlyEnabled);
        assertThat(result.grantedShards()).isZero();
        assertThat(result.checkpoint().remainder()).isEqualTo(Duration.ofMinutes(10));
    }

    @Test
    void disabledActivityPreservesRemainderAndMovesCheckpointForward() {
        var result = calculator.calculate(state(START, Duration.ofHours(12)), true,
                START.plus(Duration.ofDays(10)), RadioOfflineAccrual.Policy.defaults(false, START));
        assertThat(result.grantedShards()).isZero();
        assertThat(result.checkpoint().remainder()).isEqualTo(Duration.ofHours(12));
        assertThat(result.checkpoint().lastActivityAt()).isEqualTo(START.plus(Duration.ofDays(10)));
    }

    @Test
    void clockRegressionNeverMovesTheCheckpointBackwards() {
        var original = state(START.plusSeconds(1), Duration.ofHours(12));
        var result = calculator.calculate(original, true, START, policy);
        assertThat(result.grantedShards()).isZero();
        assertThat(result.checkpoint()).isEqualTo(original);
    }

    @Test
    void purePreviewDoesNotMutateTheCheckpointOrDependOnProcessMemory() {
        var original = state(START, Duration.ofHours(12));
        var now = START.plus(Duration.ofHours(12));
        var preview = calculator.calculate(original, true, now, policy);
        var afterRestart = new RadioOfflineAccrual().calculate(original, true, now, policy);
        assertThat(original).isEqualTo(state(START, Duration.ofHours(12)));
        assertThat(preview).isEqualTo(afterRestart);
        assertThat(preview.grantedShards()).isEqualTo(1);
    }

    @Test
    void preservesSubsecondTimeAcrossLongOfflineGaps() {
        var first = calculator.calculate(state(START, Duration.ZERO), true,
                START.plus(Duration.ofHours(12)).plusMillis(500), policy);
        var second = calculator.calculate(first.checkpoint(), true,
                START.plus(Duration.ofDays(1)).plusMillis(500), policy);
        assertThat(second.grantedShards()).isEqualTo(1);
        assertThat(second.checkpoint().remainder()).isEqualTo(Duration.ofMillis(500));
    }

    private RadioOfflineAccrual.Checkpoint state(Instant at, Duration remainder) {
        return new RadioOfflineAccrual.Checkpoint(at, remainder);
    }
}
