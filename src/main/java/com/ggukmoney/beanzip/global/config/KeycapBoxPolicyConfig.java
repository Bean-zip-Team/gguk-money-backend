package com.ggukmoney.beanzip.global.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import com.ggukmoney.beanzip.global.config.entity.AppConfig;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

/** Explicit one-way economic activation; legacy open limits only control opens and notifications. */
@Component
@RequiredArgsConstructor
public class KeycapBoxPolicyConfig {
    private static final Logger log = LoggerFactory.getLogger(KeycapBoxPolicyConfig.class);
    public static final String KEY_OPEN_CYCLE_DURATION_SECONDS = "keycapBox.openCycle.durationSeconds";
    public static final String KEY_FREE_OPEN_LIMIT = "keycapBox.freeOpen.limit";
    public static final String KEY_AD_OPEN_LIMIT = "keycapBox.adOpen.limit";
    public static final String KEY_BULK_OPEN_LIMIT = "keycapBox.bulkOpen.limit";
    public static final String KEY_SHARD_ENABLED = "keycap.economy.shard.enabled";
    public static final String KEY_DRAW_PRICE = "keycap.draw.price";
    public static final Map<String,String> DEFAULT_VALUES = Map.of(
            KEY_OPEN_CYCLE_DURATION_SECONDS,"3600",KEY_FREE_OPEN_LIMIT,"2",KEY_AD_OPEN_LIMIT,"6",
            KEY_BULK_OPEN_LIMIT,"30",KEY_DRAW_PRICE,"5",KEY_SHARD_ENABLED,"false");
    private final AppConfigBatchLoader batchLoader;
    private volatile Snapshot cache = new Snapshot(DEFAULT_VALUES, false);

    @PostConstruct @Scheduled(fixedRate = 60_000)
    public void refresh() {
        try { cache = readSnapshot(Instant.now()); }
        catch (RuntimeException failure) { log.warn("Failed to refresh keycap economy policy; keeping last-known-good", failure); }
    }

    /** Fresh immutable economics for a mutation. Keep price and phase from the same database read. */
    public Snapshot requestSnapshot() {
        try { return readSnapshot(Instant.now()); }
        catch (RuntimeException failure) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"KEYCAP_ECONOMY_POLICY_UNAVAILABLE",failure);
        }
    }

    /** Mutating callers invoke this inside the common user transaction: never trust a stale phase cache. */
    public boolean isShardEconomyEnabled() {
        Snapshot current=requestSnapshot();
        cache=current;
        return current.shardEconomy();
    }

    private Snapshot readSnapshot(Instant now) {
        var rows = new ArrayList<>(batchLoader.loadWithHistory(DEFAULT_VALUES.keySet(), KEY_SHARD_ENABLED, now));
        rows.sort(Comparator.comparing(AppConfig::getEffectiveAt)
                .thenComparing(AppConfig::getId, Comparator.nullsFirst(Comparator.naturalOrder())));
        Map<String,String> next = new HashMap<>(cache.values());
        boolean shard = false;
        for (AppConfig row : rows) {
            String key=row.getConfigKey();
            if (!DEFAULT_VALUES.containsKey(key) || row.getEffectiveAt().isAfter(now)) continue;
            String raw=row.getConfigValue().trim();
            if (KEY_SHARD_ENABLED.equals(key)) {
                if (!"true".equals(raw) && !"false".equals(raw))
                    throw new IllegalArgumentException("Invalid economic activation history");
                shard |= Boolean.parseBoolean(raw);
                next.put(key,raw);
                continue;
            }
            try {
                int value=Integer.parseInt(raw);
                boolean positive=KEY_OPEN_CYCLE_DURATION_SECONDS.equals(key) || KEY_DRAW_PRICE.equals(key);
                if (value>=0 && (!positive || value>0)) next.put(key,Integer.toString(value));
            } catch (NumberFormatException invalid) {
                log.warn("Invalid keycap policy value for {}; retaining last known value",key);
            }
        }
        return new Snapshot(Map.copyOf(next),shard);
    }

    public Duration openCycleDuration() { return Duration.ofSeconds(value(KEY_OPEN_CYCLE_DURATION_SECONDS)); }
    public int freeOpenLimit() { return value(KEY_FREE_OPEN_LIMIT); }
    public int adOpenLimit() { return value(KEY_AD_OPEN_LIMIT); }
    public int bulkOpenLimit() { return value(KEY_BULK_OPEN_LIMIT); }
    public int drawPrice() { return value(KEY_DRAW_PRICE); }
    private int value(String key) { return Integer.parseInt(cache.values().get(key)); }
    public record Snapshot(Map<String,String> values, boolean shardEconomy) {
        public Snapshot { values=Map.copyOf(values); }
        public int drawPrice() { return Integer.parseInt(values.get(KEY_DRAW_PRICE)); }
    }
}
