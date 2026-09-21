package com.ggukmoney.beanzip.global.config;

import tools.jackson.databind.ObjectMapper;
import com.ggukmoney.beanzip.global.config.repository.AppConfigRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 프로모션 정책. {@link KeycapBoxPolicyConfig} 와 같은 60초 캐시 방식이다.
 *
 * <p><b>{@code TapConfigSeeder} 에 등록하지 않는다.</b> 시더는 "최신 값이 코드 기본값과
 * 다르면 코드 기본값으로 새 행을 넣는" 동작이라, 등록하면 배포할 때마다 운영자가 켠 킬스위치가
 * 꺼지고 테스터 목록이 비워진다. 같은 이유로 {@code MANAGED_PREFIXES} 에도 넣지 않는다 —
 * 넣으면 orphan 경고가 매 부팅 뜬다.
 *
 * <p>일관성을 이유로 시더에 등록하지 말 것. 이 편차는 의도된 것이다.
 */
@Component
@RequiredArgsConstructor
public class PromotionPolicyConfig {

    private static final Logger log = LoggerFactory.getLogger(PromotionPolicyConfig.class);

    public static final String KEYCAP_FIVE_PROMOTION_CODE = "KEYCAP_FIVE_COMPLETE";
    public static final String TAP_THOUSAND_PROMOTION_CODE = "TAP_THOUSAND_COMPLETE";

    public static final String KEY_ENABLED = "promotion.keycapFive.enabled";
    public static final String KEY_EXECUTION_ENABLED = "promotion.keycapFive.executionEnabled";
    public static final String KEY_THRESHOLD = "promotion.keycapFive.threshold";
    public static final String KEY_AMOUNT = "promotion.keycapFive.amount";
    public static final String KEY_LAUNCH_AT = "promotion.keycapFive.launchAt";
    public static final String KEY_TAP_ENABLED = "promotion.tapThousand.enabled";
    public static final String KEY_TAP_THRESHOLD = "promotion.tapThousand.threshold";
    public static final String KEY_TAP_AMOUNT = "promotion.tapThousand.amount";
    public static final String KEY_TAP_LAUNCH_AT = "promotion.tapThousand.launchAt";

    /** 제외 목록과 실행 킬스위치는 프로모션 공용이다. */
    public static final String KEY_EXCLUDED_USER_IDS = "promotion.excludedUserIds";

    /**
     * 미션이 셋 이상이 되면 키·접근자를 미션 이름으로 파라미터화한다. 둘까지는 명시적으로
     * 나열하는 편이 읽기 쉽다.
     */
    private static final Map<String, String> DEFAULT_VALUES = Map.ofEntries(
            Map.entry(KEY_ENABLED, "false"),
            Map.entry(KEY_EXECUTION_ENABLED, "false"),
            Map.entry(KEY_THRESHOLD, "5"),
            Map.entry(KEY_AMOUNT, "500"),
            Map.entry(KEY_LAUNCH_AT, ""),
            Map.entry(KEY_TAP_ENABLED, "false"),
            Map.entry(KEY_TAP_THRESHOLD, "1000"),
            Map.entry(KEY_TAP_AMOUNT, "5"),
            Map.entry(KEY_TAP_LAUNCH_AT, ""),
            Map.entry(KEY_EXCLUDED_USER_IDS, "[]")
    );

    /**
     * 캐시 대상 키. 제외 목록은 빠져 있다 — 테스터를 넣고 최대 60초간 지급되는 창을 없애려고
     * {@link #excludedUserIds()} 가 매번 직접 읽는다.
     */
    private static final Set<String> CACHED_KEYS = DEFAULT_VALUES.keySet().stream()
            .filter(key -> !KEY_EXCLUDED_USER_IDS.equals(key))
            .collect(Collectors.toUnmodifiableSet());

    private final AppConfigBatchLoader batchLoader;
    private final AppConfigRepository appConfigRepository;
    private final ObjectMapper objectMapper;

    private final Map<String, String> cache = new ConcurrentHashMap<>();

    /**
     * 키마다 쿼리를 날리지 않고 {@link AppConfigBatchLoader} 로 한 번에 읽는다 (BEA-256).
     * 한 라운드가 통째로 실패하면 다음 라운드가 60초 뒤 재시도하고, 그동안은 last-known-good 이다.
     */
    @PostConstruct
    @Scheduled(fixedRate = 60_000)
    public void refresh() {
        try {
            Map<String, String> loaded = batchLoader.load(CACHED_KEYS, Instant.now());
            loaded.forEach((key, rawValue) -> {
                String value = stripJsonString(rawValue);
                if (value != null) {
                    cache.put(key, value);
                }
            });
        } catch (RuntimeException exception) {
            log.warn("Failed to refresh promotion policy config; fallback=last-known-good", exception);
        }
    }

    public boolean issuingEnabled() {
        return Boolean.parseBoolean(resolve(KEY_ENABLED).trim());
    }

    public boolean executionEnabled() {
        return Boolean.parseBoolean(resolve(KEY_EXECUTION_ENABLED).trim());
    }

    public int threshold() {
        return positiveInt(KEY_THRESHOLD);
    }

    public long amount() {
        return positiveLong(KEY_AMOUNT);
    }

    /** 1,000번 누르기 미션 (BEA-278). 키캡과 설정을 공유하지 않는다. */
    public boolean tapThousandIssuingEnabled() {
        return Boolean.parseBoolean(resolve(KEY_TAP_ENABLED).trim());
    }

    public int tapThousandThreshold() {
        return positiveInt(KEY_TAP_THRESHOLD);
    }

    public long tapThousandAmount() {
        return positiveLong(KEY_TAP_AMOUNT);
    }

    public Optional<Instant> tapThousandLaunchAt() {
        return instantAt(KEY_TAP_LAUNCH_AT);
    }

    /**
     * 소급 커트오프. 이 시각 이후에 완성된 키캡만 센다.
     *
     * <p>비어 있으면 {@link Optional#empty()} 다. 호출자는 지급하지 않아야 한다 — 값을 못 읽는
     * 상태에서 지급하면 출시 시점 이미 5개 이상인 유저 전원에게 나가고, 그 돈은 회수되지 않는다.
     */
    public Optional<Instant> launchAt() {
        return instantAt(KEY_LAUNCH_AT);
    }

    private Optional<Instant> instantAt(String key) {
        String raw = resolve(key);
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(Instant.parse(raw.trim()));
        } catch (DateTimeParseException exception) {
            log.error("Invalid {}; promotion will not be granted. value={}", key, raw, exception);
            return Optional.empty();
        }
    }

    /**
     * 제외 대상 사용자. 캐시를 우회해 매번 직접 읽는다.
     *
     * <p>읽지 못하면 빈 목록이 아니라 {@link Optional#empty()} 를 돌려준다. "목록 없음"으로
     * 떨어져 전원 지급되는 것을 막기 위한 fail-closed 다.
     */
    public Optional<Set<UUID>> excludedUserIds() {
        try {
            Optional<String> raw = appConfigRepository
                    .findFirstByConfigKeyAndEffectiveAtLessThanEqualOrderByEffectiveAtDesc(
                            KEY_EXCLUDED_USER_IDS, Instant.now())
                    .map(config -> config.getConfigValue());
            if (raw.isEmpty()) {
                return Optional.of(Collections.emptySet());
            }
            String[] values = objectMapper.readValue(raw.get(), String[].class);
            return Optional.of(java.util.Arrays.stream(values)
                    .map(String::trim)
                    .filter(value -> !value.isEmpty())
                    .map(UUID::fromString)
                    .collect(Collectors.toUnmodifiableSet()));
        } catch (Exception exception) {
            log.error("Failed to read {}; promotion will not be granted", KEY_EXCLUDED_USER_IDS, exception);
            return Optional.empty();
        }
    }

    private long positiveLong(String key) {
        String raw = resolve(key).trim();
        try {
            long value = Long.parseLong(raw);
            if (value <= 0) {
                throw new IllegalStateException(key + " must be positive");
            }
            return value;
        } catch (NumberFormatException exception) {
            throw new IllegalStateException(key + " must be a number", exception);
        }
    }

    private int positiveInt(String key) {
        try {
            int value = Integer.parseInt(resolve(key).trim());
            if (value <= 0) {
                throw new IllegalStateException(key + " must be positive");
            }
            return value;
        } catch (NumberFormatException exception) {
            throw new IllegalStateException(key + " must be an integer", exception);
        }
    }

    private String resolve(String key) {
        return cache.getOrDefault(key, DEFAULT_VALUES.get(key));
    }

    /** config_value 는 jsonb 라 문자열이 따옴표를 포함해 저장된다. OnboardingRewardConfig 선례. */
    private static String stripJsonString(String rawValue) {
        if (rawValue == null) {
            return null;
        }
        String trimmed = rawValue.trim();
        if (trimmed.length() >= 2 && trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
            return trimmed.substring(1, trimmed.length() - 1);
        }
        return trimmed;
    }
}
