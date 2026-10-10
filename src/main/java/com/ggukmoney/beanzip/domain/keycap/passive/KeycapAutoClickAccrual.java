package com.ggukmoney.beanzip.domain.keycap.passive;

import java.math.BigInteger;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/** Pure calculation. Fractions are clicks, not elapsed time, so changing a rate never reprices them. */
public final class KeycapAutoClickAccrual {
    public static final long UNITS_PER_CLICK = 86_400_000_000_000L;
    private static final BigInteger UNIT = BigInteger.valueOf(UNITS_PER_CLICK);

    public Result calculate(Checkpoint checkpoint, Instant now, Policy policy) {
        Objects.requireNonNull(checkpoint);
        Objects.requireNonNull(now);
        Objects.requireNonNull(policy);
        if (now.isBefore(checkpoint.lastActivityAt())) return new Result(0, checkpoint, false);
        Instant start = checkpoint.lastActivityAt().isAfter(policy.enabledAt())
                ? checkpoint.lastActivityAt() : policy.enabledAt();
        if (!policy.enabled() || checkpoint.clicksPerDay() == 0 || !now.isAfter(start)) {
            return new Result(0, checkpoint.at(now, checkpoint.remainderNumerator()), false);
        }
        Duration elapsed = Duration.between(start, now);
        BigInteger nanos = BigInteger.valueOf(elapsed.getSeconds()).multiply(BigInteger.valueOf(1_000_000_000L))
                .add(BigInteger.valueOf(elapsed.getNano()));
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

    public record Policy(boolean enabled, Instant enabledAt, int capDays) {
        public Policy {
            Objects.requireNonNull(enabledAt);
            if (capDays < 1) throw new IllegalArgumentException("Invalid automatic click cap");
        }
    }
    public record Result(int grantedClicks, Checkpoint checkpoint, boolean capped) {}
}
