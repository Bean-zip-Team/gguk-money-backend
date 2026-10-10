package com.ggukmoney.beanzip.domain.keycap.passive;

import com.ggukmoney.beanzip.domain.keycap.KeycapCodes;
import com.ggukmoney.beanzip.domain.keycap.entity.Keycap.Grade;

import java.util.Map;
import java.util.Objects;

import static com.ggukmoney.beanzip.domain.keycap.entity.Keycap.Grade.*;

/**
 * Immutable calculation policy. Resolve only the actual equipped keycap; display fallbacks are not equipment.
 * The BEA-329 wallet/level adapters are intentionally separate from these calculations.
 */
public final class KeycapPassivePolicy {
    private final Map<Grade, Growth> growths;
    private final Map<String, Profile> profiles;

    public KeycapPassivePolicy(Map<Grade, Growth> growths, Map<String, Profile> profiles) {
        this.growths = Map.copyOf(growths);
        this.profiles = Map.copyOf(profiles);
        for (Profile profile : this.profiles.values()) {
            if (!this.growths.containsKey(profile.grade())) {
                throw new IllegalArgumentException("Missing passive growth for " + profile.grade());
            }
        }
    }

    public static KeycapPassivePolicy defaults() {
        Critical none = Critical.NONE;
        return new KeycapPassivePolicy(
                Map.of(COMMON, new Growth(50, 0.04, 0.10, 60, 12, 180), RARE, new Growth(45, 0.08, 0.18, 100, 20, 300),
                        EPIC, new Growth(20, 0.12, 0.25, 160, 32, 480), LEGENDARY, new Growth(5, 0.18, 0.35, 300, 60, 900)),
                Map.ofEntries(
                        Map.entry("main", new Profile(COMMON, new Critical(0.10, 2), none, none)),
                        Map.entry("cheer", new Profile(COMMON, none, none, none, true)),
                        Map.entry("dolphin", new Profile(COMMON, none, new Critical(0.06, 2), none)),
                        Map.entry("lucky", new Profile(COMMON, none, new Critical(0.015, 5), none)),
                        Map.entry("redlego", new Profile(COMMON, none, none, new Critical(0.10, 2))),
                        Map.entry("yellowlego", new Profile(COMMON, none, none, new Critical(0.025, 5))),
                        Map.entry("biscuit", new Profile(RARE, new Critical(0.18, 2), none, none)),
                        Map.entry("jellyfoot", new Profile(RARE, none, new Critical(0.10, 2), none)),
                        Map.entry("pinkjelly", new Profile(RARE, none, none, none, true)),
                        Map.entry("earth", new Profile(EPIC, new Critical(0.15, 2), new Critical(0.09, 2), none)),
                        Map.entry("moon", new Profile(EPIC, none, none, new Critical(0.15, 2), true)),
                        Map.entry("space", new Profile(EPIC, new Critical(0.22, 2), none, new Critical(0.22, 2))),
                        Map.entry("pudding", new Profile(LEGENDARY, new Critical(0.20, 2),
                                new Critical(0.13, 2), new Critical(0.20, 2))),
                        Map.entry(KeycapCodes.RADIO, new Profile(LEGENDARY, new Critical(0.28, 2), none, new Critical(0.28, 2), true))
                ));
    }

    public Map<Grade, Growth> growths() {
        return growths;
    }

    public Map<String, Profile> profiles() {
        return profiles;
    }

    public Effects effects(String equippedCode, int level) {
        if (level < 1) {
            throw new IllegalArgumentException("Keycap level must be positive");
        }
        Profile profile = equippedCode == null ? null : profiles.get(equippedCode);
        if (profile == null) {
            return Effects.NONE;
        }
        Growth growth = growths.get(profile.grade());
        double strength = growth.scale(level);
        return new Effects(profile.shard().scale(strength), profile.click().scale(strength),
                profile.point().scale(strength), growth.capLevel(), level >= growth.capLevel(),
                profile.autoClick() ? growth.autoClicks(level) : 0,
                profile.autoClick() ? growth.autoClickCap() : 0,
                profile.autoClick() && growth.autoClicks(level) >= growth.autoClickCap());
    }

    public record Growth(int capLevel, double startStrength, double maxStrength,
                         int autoClickBase, int autoClickPerLevel, int autoClickCap) {
        public Growth(int capLevel, double startStrength, double maxStrength) {
            this(capLevel, startStrength, maxStrength, 0, 0, 0);
        }
        public Growth {
            if (capLevel < 2 || !Double.isFinite(startStrength) || !Double.isFinite(maxStrength)
                    || startStrength <= 0 || maxStrength < startStrength || maxStrength > 1
                    || autoClickBase < 0 || autoClickPerLevel < 0 || autoClickCap < autoClickBase) {
                throw new IllegalArgumentException("Invalid passive growth");
            }
        }

        public int autoClicks(int level) {
            if (level < 1) throw new IllegalArgumentException("Keycap level must be positive");
            return (int) Math.min((long) autoClickBase + (long) (level - 1) * autoClickPerLevel, autoClickCap);
        }

        private double scale(int level) {
            double t = Math.clamp((level - 1.0) / (capLevel - 1.0), 0.0, 1.0);
            double startRatio = startStrength / maxStrength;
            return startRatio + (1 - startRatio) * t;
        }
    }

    public record Critical(double probability, int multiplier) {
        public static final Critical NONE = new Critical(0, 1);

        public Critical {
            if (!Double.isFinite(probability) || probability < 0 || probability > 1 || multiplier < 1
                    || (multiplier == 1 && probability != 0)) {
                throw new IllegalArgumentException("Invalid passive critical effect");
            }
        }

        private Critical scale(double factor) {
            return new Critical(probability * factor, multiplier);
        }

        public double expectedMultiplier() {
            return 1 + probability * (multiplier - 1);
        }
    }

    public record Profile(Grade grade, Critical shard, Critical click, Critical point, boolean autoClick) {
        public Profile(Grade grade, Critical shard, Critical click, Critical point) {
            this(grade, shard, click, point, false);
        }
        public Profile {
            Objects.requireNonNull(grade);
            Objects.requireNonNull(shard);
            Objects.requireNonNull(click);
            Objects.requireNonNull(point);
        }
    }

    public record Effects(Critical shard, Critical click, Critical point, int capLevel, boolean capReached,
                          int autoClicksPerDay, int autoClickCap, boolean autoClickCapReached) {
        public static final Effects NONE = new Effects(Critical.NONE, Critical.NONE, Critical.NONE, 0, false, 0, 0, false);
    }
}
