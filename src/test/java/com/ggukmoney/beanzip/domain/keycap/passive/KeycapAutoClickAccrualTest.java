package com.ggukmoney.beanzip.domain.keycap.passive;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import com.ggukmoney.beanzip.domain.keycap.passive.KeycapAutoClickAccrual.*;
import static com.ggukmoney.beanzip.domain.keycap.passive.KeycapAutoClickAccrual.UNITS_PER_CLICK;

class KeycapAutoClickAccrualTest {
    private final Instant start = Instant.parse("2026-10-10T00:00:00Z");
    private final KeycapAutoClickAccrual calculator = new KeycapAutoClickAccrual();
    private final Policy policy = new Policy(true, start, 7);

    @ParameterizedTest @ValueSource(ints = {60,100,160,300,900})
    void frequentRequestsHaveExactlyTheSameDailyReward(int rate) {
        Checkpoint checkpoint = Checkpoint.initial(start, "radio", 1, rate);
        int total = 0;
        for (int i = 1; i <= 144; i++) {
            Result result = calculator.calculate(checkpoint, start.plusSeconds(i * 600L), policy);
            total += result.grantedClicks();
            checkpoint = result.checkpoint();
        }
        assertThat(total).isEqualTo(rate);
        assertThat(checkpoint.remainderNumerator()).isZero();
    }

    @Test void preservesSubsecondFractionAcrossRateChangesAndEquipmentGaps() {
        Result first = calculator.calculate(Checkpoint.initial(start, "cheer", 1, 60), start.plusNanos(1), policy);
        assertThat(first.grantedClicks()).isZero();
        assertThat(first.checkpoint().remainderNumerator()).isEqualTo(60);
        Checkpoint none = first.checkpoint().withEquipment(null, 0, 0);
        Result gap = calculator.calculate(none, start.plus(Duration.ofDays(1)), policy);
        assertThat(gap.checkpoint().remainderNumerator()).isEqualTo(60);
        Result changed = calculator.calculate(gap.checkpoint().withEquipment("radio", 2, 360),
                start.plus(Duration.ofDays(2)), policy);
        assertThat(changed.grantedClicks()).isEqualTo(360);
        assertThat(changed.checkpoint().remainderNumerator()).isEqualTo(60);
    }

    @ParameterizedTest @ValueSource(ints = {7,30})
    void appliesSevenDaysOfClicksAndDiscardsOverflow(int days) {
        Result result = calculator.calculate(Checkpoint.initial(start,"cheer",1,60),
                start.plus(Duration.ofDays(days)), policy);
        assertThat(result.grantedClicks()).isEqualTo(420);
        assertThat(result.capped()).isEqualTo(days > 7);
        assertThat(result.checkpoint().remainderNumerator()).isZero();
        assertThat(calculator.calculate(result.checkpoint(), result.checkpoint().lastActivityAt(), policy).grantedClicks()).isZero();
    }

    @Test void noRetroactiveActivationAndNoClockRegression() {
        Checkpoint checkpoint = Checkpoint.initial(start.minus(Duration.ofDays(1)),"radio",1,300);
        assertThat(calculator.calculate(checkpoint,start,policy).grantedClicks()).isZero();
        assertThat(calculator.calculate(checkpoint,start.plus(Duration.ofDays(1)),policy).grantedClicks()).isEqualTo(300);
        assertThat(calculator.calculate(checkpoint,checkpoint.lastActivityAt().minusNanos(1),policy).checkpoint()).isSameAs(checkpoint);
        assertThat(calculator.calculate(checkpoint,start,new Policy(false,start,7)).grantedClicks()).isZero();
    }

    @Test void extremeElapsedTimeCannotOverflowBeforeTheCap() {
        Result result = calculator.calculate(Checkpoint.initial(start,"radio",11,900),Instant.MAX,policy);
        assertThat(result.grantedClicks()).isEqualTo(6300);
        assertThat(result.capped()).isTrue();
    }

    @Test void rejectsInvalidFractionsRatesAndPolicies() {
        assertThatThrownBy(() -> new Checkpoint(start,-1,"radio",1,300)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Checkpoint(start,KeycapAutoClickAccrual.UNITS_PER_CLICK,"radio",1,300)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Checkpoint.initial(start,null,0,300)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Policy(true,start,0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void disabledPeriodPreservesAndPaysClicksEarnedBeforeItWasDisabled() {
        var historical = new Policy(List.of(new ActivePeriod(start,start.plus(Duration.ofDays(2)))),7);
        var result = calculator.calculate(Checkpoint.initial(start,"cheer",1,60),
                start.plus(Duration.ofDays(5)),historical);
        assertThat(result.grantedClicks()).isEqualTo(120);
        assertThat(calculator.calculate(result.checkpoint(),start.plus(Duration.ofDays(6)),historical).grantedClicks()).isZero();
    }

    @Test void sumsMultipleActivePeriodsAndClipsThemToTheCheckpointAndNow() {
        var historical = new Policy(List.of(new ActivePeriod(start,start.plus(Duration.ofDays(1))),
                new ActivePeriod(start.plus(Duration.ofDays(3)),start.plus(Duration.ofDays(4))),
                new ActivePeriod(start.plus(Duration.ofDays(5)),null)),7);
        var checkpoint = Checkpoint.initial(start.plus(Duration.ofHours(12)),"cheer",1,60);
        var result = calculator.calculate(checkpoint,start.plus(Duration.ofDays(6)),historical);
        assertThat(result.grantedClicks()).isEqualTo(150);
        assertThat(result.checkpoint().remainderNumerator()).isZero();
    }

    @Test void inactiveTimeDoesNotConsumeTheAccrualCapAndFractionSurvivesIt() {
        var historical = new Policy(List.of(new ActivePeriod(start,start.plus(Duration.ofDays(1))),
                new ActivePeriod(start.plus(Duration.ofDays(20)),start.plus(Duration.ofDays(26)))),7);
        var result = calculator.calculate(Checkpoint.initial(start,"cheer",1,60),
                start.plus(Duration.ofDays(30)),historical);
        assertThat(result.grantedClicks()).isEqualTo(420);
        assertThat(result.capped()).isFalse();
        var fraction = new Checkpoint(start.plus(Duration.ofDays(1)),UNITS_PER_CLICK/2,"cheer",1,60);
        var gap = calculator.calculate(fraction,start.plus(Duration.ofDays(10)),historical);
        assertThat(gap.grantedClicks()).isZero();
        assertThat(gap.checkpoint().remainderNumerator()).isEqualTo(UNITS_PER_CLICK/2);
    }

    @Test void rejectsOverlappingOrUnorderedActivePeriods() {
        assertThatThrownBy(() -> new ActivePeriod(start,start.minusSeconds(1))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Policy(List.of(new ActivePeriod(start,null),
                new ActivePeriod(start.plusSeconds(1),null)),7)).isInstanceOf(IllegalArgumentException.class);
    }
}
