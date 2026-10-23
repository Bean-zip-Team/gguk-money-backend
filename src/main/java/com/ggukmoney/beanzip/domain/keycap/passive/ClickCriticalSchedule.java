package com.ggukmoney.beanzip.domain.keycap.passive;

import com.ggukmoney.beanzip.domain.keycap.passive.KeycapPassivePolicy.Critical;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.BitSet;
import java.util.List;
import java.util.UUID;

/**
 * Click criticals (②) as a pure function of (user, manual tap number), so the server can announce them
 * before the taps arrive and the app can show them on the tap itself (BEA-367).
 *
 * <p>A tap is critical when its keyed hash falls below the equipped probability. The draw ignores the
 * keycap, so a lower probability always selects a subset of a higher one's taps; re-equipping can
 * never re-roll the announced taps. The key stays on the server: the app sees only {@link #WINDOW} taps ahead.
 */
@Component
public class ClickCriticalSchedule {
    /** The app flushes every 100 taps, so the previous announcement still covers a late response. */
    public static final int WINDOW = 300;
    private static final String ALGORITHM = "HmacSHA256";

    private final SecretKeySpec key;

    /** Falls back to the JWT secret; set the dedicated key before rotating that one, or every schedule re-draws. */
    public ClickCriticalSchedule(@Value("${app.tap.click-critical-secret:${app.auth.jwt.secret}}") String secret) {
        // Derived rather than shared, so tap hashes are never JWT signatures.
        this.key = new SecretKeySpec(hmac(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), ALGORITHM),
                "click-critical-schedule:v1".getBytes(StandardCharsets.UTF_8)), ALGORITHM);
    }

    /** Applies the criticals of the {@code acceptedCount} manual taps after the first {@code countedTaps}. */
    public ClickResult roll(UUID userId, long countedTaps, Critical click, int acceptedCount, int pointEligibleRawCount) {
        if (acceptedCount < 0 || pointEligibleRawCount < 0 || pointEligibleRawCount > acceptedCount) {
            throw new IllegalArgumentException("Invalid accepted tap prefix");
        }
        var hits = criticals(userId, countedTaps, acceptedCount, click);
        int extra = click.multiplier() - 1;
        int all = hits.cardinality();
        int eligible = hits.get(0, pointEligibleRawCount).cardinality();
        return new ClickResult(acceptedCount, Math.addExact(acceptedCount, Math.multiplyExact(all, extra)),
                Math.addExact(pointEligibleRawCount, Math.multiplyExact(eligible, extra)), all);
    }

    /** 1-based offsets of the criticals among the next {@link #WINDOW} manual taps, or null without the effect. */
    public List<Integer> upcoming(UUID userId, long countedTaps, Critical click) {
        if (click.probability() == 0) return null;
        return criticals(userId, countedTaps, WINDOW, click).stream().map(i -> i + 1).boxed().toList();
    }

    private BitSet criticals(UUID userId, long countedTaps, int count, Critical click) {
        var hits = new BitSet(count);
        if (click.probability() == 0) return hits;
        var mac = mac(key);
        var input = ByteBuffer.allocate(24)
                .putLong(userId.getMostSignificantBits()).putLong(userId.getLeastSignificantBits());
        for (int i = 0; i < count; i++) {
            input.putLong(16, Math.addExact(countedTaps, i + 1L));
            long bits = ByteBuffer.wrap(mac.doFinal(input.array())).getLong();
            if ((bits >>> 11) * 0x1.0p-53 < click.probability()) hits.set(i);
        }
        return hits;
    }

    private static byte[] hmac(SecretKeySpec key, byte[] message) {
        return mac(key).doFinal(message);
    }

    private static Mac mac(SecretKeySpec key) {
        try {
            var mac = Mac.getInstance(ALGORITHM);
            mac.init(key);
            return mac;
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("HmacSHA256 is unavailable", exception);
        }
    }

    public record ClickResult(int acceptedCount, int effectiveCount, int pointEligibleEffectiveCount, int criticalCount) {}
}
