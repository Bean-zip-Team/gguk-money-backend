package com.ggukmoney.beanzip.domain.keycap.passive;

import com.ggukmoney.beanzip.domain.keycap.KeycapCodes;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Pure automatic accrual calculation for all radio-equipped time recorded in the checkpoint.
 * The transaction adapter must lock the user, credit the BEA-329 wallet and persist
 * the returned checkpoint with any new equipment atomically. Preview callers persist nothing.
 */
public final class RadioOfflineAccrual {
    public Result calculate(Checkpoint checkpoint, Instant now, Policy policy) {
        Objects.requireNonNull(checkpoint);
        Objects.requireNonNull(now);
        Objects.requireNonNull(policy);
        if (now.isBefore(checkpoint.lastActivityAt())) {
            return new Result(0, checkpoint, false);
        }
        Duration accrued = Duration.ZERO;
        if (policy.enabled() && KeycapCodes.RADIO.equals(checkpoint.equippedKeycapCode())) {
            Instant start = checkpoint.lastActivityAt().isAfter(policy.enabledAt())
                    ? checkpoint.lastActivityAt() : policy.enabledAt();
            if (now.isAfter(start)) {
                accrued = Duration.between(start, now);
            }
        }
        // Unequipped or disabled intervals preserve the incomplete period without granting a shard.
        if (accrued.isZero()) {
            return new Result(0, new Checkpoint(now, checkpoint.remainder(), checkpoint.equippedKeycapCode()), false);
        }
        Duration total = checkpoint.remainder().plus(accrued);
        Duration ceiling = policy.shardInterval().multipliedBy(policy.maxShards());
        boolean capped = total.compareTo(ceiling) >= 0;
        if (capped) {
            total = ceiling;
        }
        int shards = Math.toIntExact(total.dividedBy(policy.shardInterval()));
        Duration remainder = total.minus(policy.shardInterval().multipliedBy(shards));
        return new Result(shards, new Checkpoint(now, remainder, checkpoint.equippedKeycapCode()), capped);
    }

    public record Checkpoint(Instant lastActivityAt, Duration remainder, String equippedKeycapCode) {
        public Checkpoint {
            Objects.requireNonNull(lastActivityAt);
            Objects.requireNonNull(remainder);
            if (remainder.isNegative()) {
                throw new IllegalArgumentException("Accrual remainder must not be negative");
            }
        }

        public static Checkpoint initial(Instant now, String equippedKeycapCode) {
            return new Checkpoint(now, Duration.ZERO, equippedKeycapCode);
        }
    }

    public record Policy(boolean enabled, Instant enabledAt, Duration shardInterval, int maxShards) {
        public Policy {
            Objects.requireNonNull(enabledAt);
            Objects.requireNonNull(shardInterval);
            if (shardInterval.isNegative() || shardInterval.isZero() || maxShards < 1) {
                throw new IllegalArgumentException("Invalid automatic accrual policy");
            }
            shardInterval.multipliedBy(maxShards); // Reject an unrepresentable ceiling before processing requests.
        }

        public static Policy defaults(boolean enabled, Instant enabledAt) {
            return new Policy(enabled, enabledAt, Duration.ofDays(1), 7);
        }
    }

    public record Result(int grantedShards, Checkpoint checkpoint, boolean capped) {}
}
