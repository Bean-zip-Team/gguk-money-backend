package com.ggukmoney.beanzip.global.config;

import com.ggukmoney.beanzip.domain.keycap.entity.Keycap.Grade;
import com.ggukmoney.beanzip.domain.keycap.passive.KeycapPassivePolicy;
import com.ggukmoney.beanzip.domain.keycap.passive.KeycapPassivePolicy.Critical;
import com.ggukmoney.beanzip.domain.keycap.passive.KeycapPassivePolicy.Growth;
import com.ggukmoney.beanzip.domain.keycap.passive.KeycapPassivePolicy.Profile;
import com.ggukmoney.beanzip.domain.keycap.passive.KeycapAutoClickAccrual.ActivePeriod;
import com.ggukmoney.beanzip.global.config.entity.AppConfig;
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
import java.util.List;
import java.util.ArrayList;
import java.util.Set;
import java.util.HashSet;

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
    public static final Set<String> CONFIG_KEYS = configKeys();

    private final AppConfigBatchLoader loader;
    private volatile Cached cache = new Cached(Map.of(), new Snapshot(false, BASE_POLICY,Instant.EPOCH,7,List.of(),false));

    public KeycapPassivePolicyConfig(AppConfigBatchLoader loader) {
        this.loader = Objects.requireNonNull(loader);
    }

    @PostConstruct
    @Scheduled(fixedRate = 60_000)
    public synchronized void refresh() {
        try {
            publish(loader.loadWithHistory(CONFIG_KEYS,KEY_ENABLED,Instant.now()));
        } catch (RuntimeException exception) {
            log.warn("Failed to refresh keycap passive policy; fallback=last-known-good", exception);
        }
    }

    public Snapshot snapshot() {
        return cache.snapshot();
    }

    /** Refresh at settlement so another pod's committed on/off changes cannot be missed. */
    public synchronized Snapshot settlementSnapshot(Instant now) {
        List<AppConfig> rows;
        try {
            rows=loader.loadWithHistory(CONFIG_KEYS,KEY_ENABLED,now);
        } catch (RuntimeException exception) {
            log.warn("Passive activation history unavailable; settlement must retry",exception);
            return unavailableSnapshot();
        }
        try {
            publish(rows);
        } catch (RuntimeException exception) {
            log.warn("Invalid passive policy; retain cache and retry settlement without advancing checkpoint",exception);
            return unavailableSnapshot();
        }
        return cache.snapshot();
    }

    private Snapshot unavailableSnapshot() {
        var previous=cache.snapshot();
        return new Snapshot(previous.enabled(),previous.policy(),previous.enabledAt(),previous.capDays(),
                previous.activePeriods(),false);
    }

    private void publish(List<AppConfig> rows) {
        Map<String,String> values=new HashMap<>();
        rows.forEach(row -> values.put(row.getConfigKey(),row.getConfigValue().trim()));
        Snapshot decoded=decode(values);
        var periods=new ArrayList<ActivePeriod>();
        Instant from=null;
        for (AppConfig row:rows) {
            if (!KEY_ENABLED.equals(row.getConfigKey())) continue;
            String flag=row.getConfigValue().trim();
            if (!"true".equalsIgnoreCase(flag) && !"false".equalsIgnoreCase(flag))
                throw new IllegalArgumentException("Invalid activation history flag");
            if (Boolean.parseBoolean(flag)) {
                if (from==null) from=row.getEffectiveAt();
            } else if (from!=null) {
                if (row.getEffectiveAt().isAfter(from)) periods.add(new ActivePeriod(from,row.getEffectiveAt()));
                from=null;
            }
        }
        if (from!=null) periods.add(new ActivePeriod(from,null));
        // Validate ordering before publishing any part of the policy.
        new com.ggukmoney.beanzip.domain.keycap.passive.KeycapAutoClickAccrual.Policy(periods,decoded.capDays());
        cache=new Cached(Map.copyOf(values),new Snapshot(decoded.enabled(),decoded.policy(),decoded.enabledAt(),
                decoded.capDays(),periods,true));
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
            double ratio=startRatio(grade,overrides);
            growths.put(grade, new Growth(Integer.parseInt(values.get(prefix + "capLevel")),ratio,1.0,
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

    /** Read legacy operational pairs only until the append-only seeder writes their equivalent ratio. */
    public static double startRatio(Grade grade,Map<String,String> overrides) {
        String prefix=PREFIX+grade.name()+".";
        Growth base=BASE_POLICY.growths().get(grade);
        double ratio;
        if (overrides.containsKey(prefix+"startRatio")) ratio=Double.parseDouble(overrides.get(prefix+"startRatio"));
        else {
            double start=Double.parseDouble(overrides.getOrDefault(prefix+"startStrength",Double.toString(base.startStrength())));
            double max=Double.parseDouble(overrides.getOrDefault(prefix+"maxStrength",Double.toString(base.maxStrength())));
            if (!Double.isFinite(start) || !Double.isFinite(max) || start<=0 || max<start || max>1)
                throw new IllegalArgumentException("Invalid legacy passive growth");
            ratio=start/max;
        }
        if (!Double.isFinite(ratio) || ratio<=0 || ratio>1) throw new IllegalArgumentException("Lv1 비율은 0보다 크고 1 이하입니다.");
        return ratio;
    }

    private static Set<String> configKeys() {
        var keys=new HashSet<>(DEFAULT_VALUES.keySet());
        BASE_POLICY.growths().keySet().forEach(grade -> {
            keys.add(PREFIX+grade.name()+".startStrength");
            keys.add(PREFIX+grade.name()+".maxStrength");
        });
        return Set.copyOf(keys);
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
            values.put(prefix + "startRatio", Double.toString(growth.startRatio()));
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

    public record Snapshot(boolean enabled, KeycapPassivePolicy policy, Instant enabledAt, int capDays,
                           List<ActivePeriod> activePeriods,boolean loaded) {
        public Snapshot { activePeriods=List.copyOf(activePeriods); }
        public Snapshot(boolean enabled, KeycapPassivePolicy policy,Instant enabledAt,int capDays) {
            this(enabled,policy,enabledAt,capDays,enabled?List.of(new ActivePeriod(enabledAt,null)):List.of(),true);
        }
        public Snapshot(boolean enabled, KeycapPassivePolicy policy) {
            this(enabled, policy, Instant.EPOCH, 7);
        }
    }
    private record Cached(Map<String, String> values, Snapshot snapshot) {}
}
