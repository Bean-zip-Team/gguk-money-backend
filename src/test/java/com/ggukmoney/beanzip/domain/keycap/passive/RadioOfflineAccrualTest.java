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

    @ParameterizedTest
    @CsvSource(value = {"radio", "pudding", "NULL"}, nullValues = "NULL")
    void firstActivityInitializesWithoutRetroactiveCredit(String equippedCode) {
        var now = START.plus(Duration.ofDays(30));
        var checkpoint = RadioOfflineAccrual.Checkpoint.initial(now, equippedCode);
        var result = calculator.calculate(checkpoint, now, policy);
        assertThat(result.grantedShards()).isZero();
        assertThat(result.checkpoint()).isEqualTo(
                new RadioOfflineAccrual.Checkpoint(now, Duration.ZERO, equippedCode));
        assertThat(result.capped()).isFalse();
    }

    @Test
    void rejectsAnUninitializedCheckpoint() {
        assertThatNullPointerException().isThrownBy(() -> calculator.calculate(null, START, policy));
    }

    @ParameterizedTest
    @CsvSource(value = {
        "radio,1,43200", "pudding,0,43200", "Radio,0,43200", "unknown,0,43200", "NULL,0,43200"
    }, nullValues = "NULL")
    void accruesOnlyForTheRadioRecordedInTheCheckpoint(String equippedCode, int shards, long remainderSeconds) {
        var result = calculator.calculate(state(START, Duration.ofHours(12), equippedCode),
                START.plus(Duration.ofDays(1)), policy);
        assertThat(result.grantedShards()).isEqualTo(shards);
        assertThat(result.checkpoint().remainder()).isEqualTo(Duration.ofSeconds(remainderSeconds));
        assertThat(result.checkpoint().lastActivityAt()).isEqualTo(START.plus(Duration.ofDays(1)));
        assertThat(result.checkpoint().equippedKeycapCode()).isEqualTo(equippedCode);
        assertThat(result.capped()).isFalse();
    }

    @ParameterizedTest
    @CsvSource({
        "1799,0,0,false", "1800,0,1800,false",
        "86399,0,86399,false", "86400,1,0,false",
        "604800,7,0,true", "2592000,7,0,true"
    })
    void respectsIdleDayAndCapBoundaries(long elapsedSeconds, int shards, long remainder, boolean capped) {
        var result = calculator.calculate(state(START, Duration.ZERO), START.plusSeconds(elapsedSeconds), policy);
        assertThat(result.grantedShards()).isEqualTo(shards);
        assertThat(result.checkpoint().remainder()).isEqualTo(Duration.ofSeconds(remainder));
        assertThat(result.checkpoint().equippedKeycapCode()).isEqualTo("radio");
        assertThat(result.capped()).isEqualTo(capped);
    }

    @Test
    void twoTwelveHourGapsPayOneShardWithoutLosingRemainder() {
        var first = calculator.calculate(state(START, Duration.ZERO), START.plus(Duration.ofHours(12)), policy);
        assertThat(first.grantedShards()).isZero();
        assertThat(first.checkpoint().remainder()).isEqualTo(Duration.ofHours(12));
        var second = calculator.calculate(first.checkpoint(), START.plus(Duration.ofDays(1)), policy);
        assertThat(second.grantedShards()).isEqualTo(1);
        assertThat(second.checkpoint().remainder()).isZero();
    }

    @ParameterizedTest
    @CsvSource(value = {"pudding", "NULL"}, nullValues = "NULL")
    void unequippedTimePausesButDoesNotEraseEarnedRemainder(String equippedCode) {
        var paused = calculator.calculate(state(START, Duration.ofHours(12), equippedCode),
                START.plus(Duration.ofDays(1)), policy);
        assertThat(paused.grantedShards()).isZero();
        assertThat(paused.checkpoint().remainder()).isEqualTo(Duration.ofHours(12));
        assertThat(paused.checkpoint().equippedKeycapCode()).isEqualTo(equippedCode);
        var radioCheckpoint = state(paused.checkpoint().lastActivityAt(), paused.checkpoint().remainder());
        var resumed = calculator.calculate(radioCheckpoint, START.plus(Duration.ofHours(36)), policy);
        assertThat(resumed.grantedShards()).isEqualTo(1);
    }

    @Test
    void switchingAwayFromRadioExcludesTheUnequippedGapAndResumesTheRemainder() {
        var first = calculator.calculate(RadioOfflineAccrual.Checkpoint.initial(START, "radio"),
                START.plus(Duration.ofHours(12)), policy);
        assertThat(first.grantedShards()).isZero();
        assertThat(first.checkpoint().remainder()).isEqualTo(Duration.ofHours(12));

        var puddingCheckpoint = state(first.checkpoint().lastActivityAt(), first.checkpoint().remainder(), "pudding");
        var paused = calculator.calculate(puddingCheckpoint, START.plus(Duration.ofHours(36)), policy);
        assertThat(paused.grantedShards()).isZero();
        assertThat(paused.checkpoint().remainder()).isEqualTo(Duration.ofHours(12));
        assertThat(paused.checkpoint().equippedKeycapCode()).isEqualTo("pudding");

        var radioCheckpoint = state(paused.checkpoint().lastActivityAt(), paused.checkpoint().remainder());
        var resumed = calculator.calculate(radioCheckpoint, START.plus(Duration.ofHours(48)), policy);
        assertThat(resumed.grantedShards()).isEqualTo(1);
        assertThat(resumed.checkpoint().remainder()).isZero();
        assertThat(resumed.checkpoint().equippedKeycapCode()).isEqualTo("radio");
    }

    @Test
    void selectingTheSameRadioAgainDoesNotResetTheRemainder() {
        var first = calculator.calculate(RadioOfflineAccrual.Checkpoint.initial(START, "radio"),
                START.plus(Duration.ofHours(12)), policy);
        var selectedAgain = calculator.calculate(first.checkpoint(), first.checkpoint().lastActivityAt(), policy);
        assertThat(selectedAgain.grantedShards()).isZero();
        assertThat(selectedAgain.checkpoint()).isEqualTo(first.checkpoint());

        var second = calculator.calculate(selectedAgain.checkpoint(), START.plus(Duration.ofDays(1)), policy);
        assertThat(second.grantedShards()).isEqualTo(1);
        assertThat(second.checkpoint().remainder()).isZero();
    }

    @Test
    void discardsExcessTimeIncludingTheFractionBeyondSevenDays() {
        var result = calculator.calculate(state(START, Duration.ofHours(12)),
                START.plus(Duration.ofDays(30)), policy);
        assertThat(result.grantedShards()).isEqualTo(7);
        assertThat(result.checkpoint().remainder()).isZero();
        assertThat(result.checkpoint().equippedKeycapCode()).isEqualTo("radio");
        var next = calculator.calculate(result.checkpoint(),
                START.plus(Duration.ofDays(30)).plus(Duration.ofHours(1)), policy);
        assertThat(next.grantedShards()).isZero();
        assertThat(next.checkpoint().remainder()).isEqualTo(Duration.ofHours(1));
    }

    @Test
    void reactivationExcludesDisabledTimeEvenWhenThereWasNoRequestDuringIt() {
        var reactivated = RadioOfflineAccrual.Policy.defaults(true, START.plus(Duration.ofHours(36)));
        var result = calculator.calculate(state(START, Duration.ofHours(12)),
                START.plus(Duration.ofDays(2)), reactivated);
        assertThat(result.grantedShards()).isEqualTo(1);
        assertThat(result.checkpoint().remainder()).isZero();
    }

    @Test
    void idleClassificationUsesActivityGapAndAccrualUsesOnlyTheEnabledPortion() {
        var now = START.plus(Duration.ofHours(2));
        var recentlyEnabled = RadioOfflineAccrual.Policy.defaults(true, now.minus(Duration.ofMinutes(10)));
        var result = calculator.calculate(state(START, Duration.ZERO), now, recentlyEnabled);
        assertThat(result.grantedShards()).isZero();
        assertThat(result.checkpoint().remainder()).isEqualTo(Duration.ofMinutes(10));
    }

    @ParameterizedTest
    @CsvSource(value = {"radio", "pudding", "NULL"}, nullValues = "NULL")
    void disabledActivityPreservesRemainderAndMovesCheckpointForward(String equippedCode) {
        var result = calculator.calculate(state(START, Duration.ofHours(12), equippedCode),
                START.plus(Duration.ofDays(10)), RadioOfflineAccrual.Policy.defaults(false, START));
        assertThat(result.grantedShards()).isZero();
        assertThat(result.checkpoint().remainder()).isEqualTo(Duration.ofHours(12));
        assertThat(result.checkpoint().lastActivityAt()).isEqualTo(START.plus(Duration.ofDays(10)));
        assertThat(result.checkpoint().equippedKeycapCode()).isEqualTo(equippedCode);
    }

    @ParameterizedTest
    @CsvSource(value = {"radio", "pudding", "NULL"}, nullValues = "NULL")
    void clockRegressionNeverMovesTheCheckpointBackwards(String equippedCode) {
        var original = state(START.plusSeconds(1), Duration.ofHours(12), equippedCode);
        var result = calculator.calculate(original, START, policy);
        assertThat(result.grantedShards()).isZero();
        assertThat(result.checkpoint()).isEqualTo(original);
    }

    @Test
    void purePreviewDoesNotMutateTheCheckpointOrDependOnProcessMemory() {
        var original = state(START, Duration.ofHours(12));
        var now = START.plus(Duration.ofHours(12));
        var preview = calculator.calculate(original, now, policy);
        var afterRestart = new RadioOfflineAccrual().calculate(original, now, policy);
        assertThat(original).isEqualTo(state(START, Duration.ofHours(12)));
        assertThat(preview).isEqualTo(afterRestart);
        assertThat(preview.grantedShards()).isEqualTo(1);
    }

    @Test
    void preservesSubsecondTimeAcrossLongOfflineGaps() {
        var first = calculator.calculate(state(START, Duration.ZERO),
                START.plus(Duration.ofHours(12)).plusMillis(500), policy);
        var second = calculator.calculate(first.checkpoint(),
                START.plus(Duration.ofDays(1)).plusMillis(500), policy);
        assertThat(second.grantedShards()).isEqualTo(1);
        assertThat(second.checkpoint().remainder()).isEqualTo(Duration.ofMillis(500));
    }

    private RadioOfflineAccrual.Checkpoint state(Instant at, Duration remainder) {
        return state(at, remainder, "radio");
    }

    private RadioOfflineAccrual.Checkpoint state(Instant at, Duration remainder, String equippedCode) {
        return new RadioOfflineAccrual.Checkpoint(at, remainder, equippedCode);
    }
}
