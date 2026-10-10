package com.ggukmoney.beanzip.global.config;

import com.ggukmoney.beanzip.domain.keycap.entity.Keycap.Grade;
import com.ggukmoney.beanzip.domain.keycap.passive.KeycapPassivePolicy;
import com.ggukmoney.beanzip.domain.keycap.passive.KeycapPassivePolicy.Critical;
import com.ggukmoney.beanzip.domain.keycap.passive.KeycapPassivePolicy.Growth;
import com.ggukmoney.beanzip.domain.keycap.passive.KeycapPassivePolicy.Profile;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/** Publishes one immutable policy per refresh; defaults do not activate rewards. */
@Component
public class KeycapPassivePolicyConfig {
    private static final Logger log = LoggerFactory.getLogger(KeycapPassivePolicyConfig.class);
    private static final String PREFIX = "keycap.passive.";
    public static final String KEY_ENABLED = PREFIX + "enabled";
    public static final String KEY_ENABLED_AT = PREFIX + "enabledAt";
    public static final String KEY_CAP_DAYS = PREFIX + "capDays";
    private static final KeycapPassivePolicy BASE_POLICY = KeycapPassivePolicy.defaults();
    public static final Map<String, String> DEFAULT_VALUES = defaultValues();

    private final AppConfigBatchLoader loader;
    private volatile Cached cache = new Cached(DEFAULT_VALUES, new Snapshot(false, BASE_POLICY));

    public KeycapPassivePolicyConfig(AppConfigBatchLoader loader) {
        this.loader = Objects.requireNonNull(loader);
    }

    @PostConstruct
    @Scheduled(fixedRate = 60_000)
    public void refresh() {
        try {
            Map<String, String> loaded = loader.load(DEFAULT_VALUES.keySet(), Instant.now());
            Map<String, String> values = new HashMap<>(cache.values());
            for (String key : DEFAULT_VALUES.keySet()) {
                if (loaded.containsKey(key)) {
                    values.put(key, loaded.get(key).trim());
                }
            }
            Snapshot next = decode(values);
            cache = new Cached(Map.copyOf(values), next);
        } catch (RuntimeException exception) {
            log.warn("Failed to refresh keycap passive policy; fallback=last-known-good", exception);
        }
    }

    public Snapshot snapshot() {
        return cache.snapshot();
    }

    public static Snapshot decode(Map<String, String> overrides) {
        Map<String, String> values = new HashMap<>(DEFAULT_VALUES);
        values.putAll(overrides);
        String enabled = values.get(KEY_ENABLED);
        if (!"true".equalsIgnoreCase(enabled) && !"false".equalsIgnoreCase(enabled)) {
            throw new IllegalArgumentException("Invalid passive enabled flag");
        }
        Map<Grade, Growth> growths = new EnumMap<>(Grade.class);
        for (Grade grade : BASE_POLICY.growths().keySet()) {
            String prefix = PREFIX + grade.name() + ".";
            growths.put(grade, new Growth(Integer.parseInt(values.get(prefix + "capLevel")),
                    Double.parseDouble(values.get(prefix + "startStrength")),
                    Double.parseDouble(values.get(prefix + "maxStrength")),
                    Integer.parseInt(values.get(prefix + "autoClickBase")),
                    Integer.parseInt(values.get(prefix + "autoClickPerLevel")),
                    Integer.parseInt(values.get(prefix + "autoClickCap"))));
        }
        Map<String, Profile> profiles = new HashMap<>();
        BASE_POLICY.profiles().forEach((code, base) -> profiles.put(code, new Profile(base.grade(),
                critical(values, code, "shard", base.shard()),
                critical(values, code, "click", base.click()),
                critical(values, code, "point", base.point()), base.autoClick())));
        int capDays = Integer.parseInt(values.get(KEY_CAP_DAYS));
        if (capDays < 1 || capDays > 30) throw new IllegalArgumentException("적립 상한은 1~30일입니다.");
        for (Growth growth : growths.values()) {
            if ((long) growth.autoClickCap() * capDays > 100_000) {
                throw new IllegalArgumentException("정산 클릭 상한은 100,000 이하입니다.");
            }
        }
        profiles.forEach((code, profile) -> {
            Profile base = BASE_POLICY.profiles().get(code);
            if (Math.max(profile.click().multiplier(), Math.max(profile.shard().multiplier(),profile.point().multiplier()))>5) {
                throw new IllegalArgumentException("크리티컬 배수는 5 이하입니다.");
            }
            if (budget(profile) > budget(base) + 1e-12) {
                throw new IllegalArgumentException(code + ": 기본 프로필의 기대배수를 초과합니다.");
            }
        });
        return new Snapshot(Boolean.parseBoolean(enabled), new KeycapPassivePolicy(growths, profiles),
                Instant.parse(unquote(values.get(KEY_ENABLED_AT))), capDays);
    }

    private static double budget(Profile profile) {
        return profile.click().expectedMultiplier()
                * Math.max(profile.shard().expectedMultiplier(), profile.point().expectedMultiplier());
    }

    private static String unquote(String value) {
        return value.startsWith("\"") && value.endsWith("\"") ? value.substring(1, value.length()-1) : value;
    }

    private static Critical critical(Map<String, String> values, String code, String axis, Critical base) {
        if (base.multiplier() == 1) {
            return Critical.NONE;
        }
        String prefix = PREFIX + code + "." + axis + ".";
        return new Critical(Double.parseDouble(values.get(prefix + "probability")),
                Integer.parseInt(values.get(prefix + "multiplier")));
    }

    private static Map<String, String> defaultValues() {
        Map<String, String> values = new HashMap<>();
        values.put(KEY_ENABLED, "false");
        values.put(KEY_ENABLED_AT, "\"1970-01-01T00:00:00Z\"");
        values.put(KEY_CAP_DAYS, "7");
        BASE_POLICY.growths().forEach((grade, growth) -> {
            String prefix = PREFIX + grade.name() + ".";
            values.put(prefix + "capLevel", Integer.toString(growth.capLevel()));
            values.put(prefix + "startStrength", Double.toString(growth.startStrength()));
            values.put(prefix + "maxStrength", Double.toString(growth.maxStrength()));
            values.put(prefix + "autoClickBase", Integer.toString(growth.autoClickBase()));
            values.put(prefix + "autoClickPerLevel", Integer.toString(growth.autoClickPerLevel()));
            values.put(prefix + "autoClickCap", Integer.toString(growth.autoClickCap()));
        });
        BASE_POLICY.profiles().forEach((code, profile) -> {
            addCritical(values, code, "shard", profile.shard());
            addCritical(values, code, "click", profile.click());
            addCritical(values, code, "point", profile.point());
        });
        return Map.copyOf(values);
    }

    private static void addCritical(Map<String, String> values, String code, String axis, Critical effect) {
        if (effect.multiplier() > 1) {
            String prefix = PREFIX + code + "." + axis + ".";
            values.put(prefix + "probability", Double.toString(effect.probability()));
            values.put(prefix + "multiplier", Integer.toString(effect.multiplier()));
        }
    }

    public record Snapshot(boolean enabled, KeycapPassivePolicy policy, Instant enabledAt, int capDays) {
        public Snapshot(boolean enabled, KeycapPassivePolicy policy) {
            this(enabled, policy, Instant.EPOCH, 7);
        }
    }
    private record Cached(Map<String, String> values, Snapshot snapshot) {}
}
