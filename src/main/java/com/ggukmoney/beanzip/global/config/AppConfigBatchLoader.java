package com.ggukmoney.beanzip.global.config;

import com.ggukmoney.beanzip.global.config.entity.AppConfig;
import com.ggukmoney.beanzip.global.config.repository.AppConfigRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.List;

@Component
@RequiredArgsConstructor
public class AppConfigBatchLoader {

    private final AppConfigRepository repository;

    public List<AppConfig> loadWithHistory(Set<String> configKeys, String historyKey, Instant now) {
        if (configKeys.isEmpty()) return List.of();
        if (!configKeys.contains(historyKey)) throw new IllegalArgumentException("History key must be requested");
        return repository.findLatestEffectiveWithHistory(configKeys,historyKey,now);
    }

    public Map<String, String> load(Set<String> configKeys, Instant now) {
        if (configKeys.isEmpty()) {
            return Map.of();
        }
        Map<String, String> values = new LinkedHashMap<>();
        for (AppConfig config : repository.findLatestEffectiveByConfigKeys(configKeys, now)) {
            if (!configKeys.contains(config.getConfigKey())) {
                continue;
            }
            if (values.putIfAbsent(config.getConfigKey(), config.getConfigValue()) != null) {
                throw new IllegalStateException("Duplicate AppConfig row for key: " + config.getConfigKey());
            }
        }
        return Map.copyOf(values);
    }
}
