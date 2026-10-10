package com.ggukmoney.beanzip.domain.keycap.passive;

import java.math.BigInteger;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.List;

/** Pure calculation. Fractions are clicks, not elapsed time, so changing a rate never reprices them. */
public final class KeycapAutoClickAccrual {
    public static final long UNITS_PER_CLICK = 86_400_000_000_000L;
    private static final BigInteger UNIT = BigInteger.valueOf(UNITS_PER_CLICK);

    public Result calculate(Checkpoint checkpoint, Instant now, Policy policy) {
        Objects.requireNonNull(checkpoint);
        Objects.requireNonNull(now);
        Objects.requireNonNull(policy);
        if (now.isBefore(checkpoint.lastActivityAt())) return new Result(0, checkpoint, false);
        if (checkpoint.clicksPerDay() == 0) {
            return new Result(0, checkpoint.at(now, checkpoint.remainderNumerator()), false);
        }
        BigInteger nanos = BigInteger.ZERO;
        for (ActivePeriod period : policy.activePeriods()) {
            Instant start = checkpoint.lastActivityAt().isAfter(period.from()) ? checkpoint.lastActivityAt() : period.from();
            Instant end = period.until() == null || now.isBefore(period.until()) ? now : period.until();
            if (end.isAfter(start)) {
                Duration elapsed = Duration.between(start,end);
                nanos = nanos.add(BigInteger.valueOf(elapsed.getSeconds()).multiply(BigInteger.valueOf(1_000_000_000L))
                        .add(BigInteger.valueOf(elapsed.getNano())));
            }
        }
        BigInteger total = nanos.multiply(BigInteger.valueOf(checkpoint.clicksPerDay()))
                .add(BigInteger.valueOf(checkpoint.remainderNumerator()));
        BigInteger ceiling = UNIT.multiply(BigInteger.valueOf(checkpoint.clicksPerDay()))
                .multiply(BigInteger.valueOf(policy.capDays()));
        boolean capped = total.compareTo(ceiling) > 0;
        BigInteger[] parts = total.min(ceiling).divideAndRemainder(UNIT);
        return new Result(parts[0].intValueExact(), checkpoint.at(now, parts[1].longValueExact()), capped);
    }

    public record Checkpoint(Instant lastActivityAt, long remainderNumerator, String equippedKeycapCode,
                             int equippedLevel, int clicksPerDay) {
        public Checkpoint {
            Objects.requireNonNull(lastActivityAt);
            if (remainderNumerator < 0 || remainderNumerator >= UNITS_PER_CLICK || clicksPerDay < 0
                    || (equippedKeycapCode == null && (equippedLevel != 0 || clicksPerDay != 0))
                    || (equippedKeycapCode != null && equippedLevel < 1)) {
                throw new IllegalArgumentException("Invalid automatic click checkpoint");
            }
        }
        public static Checkpoint initial(Instant now, String code, int level, int rate) {
            return new Checkpoint(now, 0, code, level, rate);
        }
        public Checkpoint withEquipment(String code, int level, int rate) {
            return new Checkpoint(lastActivityAt, remainderNumerator, code, level, rate);
        }
        private Checkpoint at(Instant now, long remainder) {
            return new Checkpoint(now, remainder, equippedKeycapCode, equippedLevel, clicksPerDay);
        }
    }

    /** Inclusive start, exclusive end; null end represents the current open activation. */
    public record ActivePeriod(Instant from, Instant until) {
        public ActivePeriod {
            Objects.requireNonNull(from);
            if (until != null && !until.isAfter(from)) throw new IllegalArgumentException("Invalid active period");
        }
    }

    public record Policy(List<ActivePeriod> activePeriods, int capDays) {
        public Policy(boolean enabled, Instant enabledAt, int capDays) {
            this(enabled ? List.of(new ActivePeriod(enabledAt,null)) : List.of(),capDays);
            Objects.requireNonNull(enabledAt);
        }
        public Policy {
            activePeriods = List.copyOf(activePeriods);
            if (capDays < 1) throw new IllegalArgumentException("Invalid automatic click cap");
            for (int i=1; i<activePeriods.size(); i++) {
                Instant previousEnd = activePeriods.get(i-1).until();
                if (previousEnd == null || previousEnd.isAfter(activePeriods.get(i).from()))
                    throw new IllegalArgumentException("Active periods must be ordered and non-overlapping");
            }
        }
    }
    public record Result(int grantedClicks, Checkpoint checkpoint, boolean capped) {}
}
