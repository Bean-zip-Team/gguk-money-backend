package com.ggukmoney.beanzip.global.config.ops;

import com.ggukmoney.beanzip.global.config.entity.AppConfig;
import com.ggukmoney.beanzip.global.config.repository.AppConfigRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;

/**
 * app_config 를 운영 화면에서 바꾼다. 행을 고치지 않고 새 행을 추가하므로(effective_at = 지금) 이력이 남는다.
 * 규칙({@link PolicyValueRules})이 있는 기존 키만 바꿀 수 있다. 나머지는 SQL 로 바꾼다.
 */
@Service
@RequiredArgsConstructor
public class OpsConfigService {

    private static final Logger log = LoggerFactory.getLogger(OpsConfigService.class);
    private static final int MAX_CHANGED_BY = 50;
    private static final int MAX_REASON = 200;
    private static final Pattern CONTROL_CHARACTERS = Pattern.compile("\\p{Cntrl}");

    private final AppConfigRepository repository;
    private final AppConfigValueValidator validator;
    private final PolicyValueRules rules;

    @Transactional(readOnly = true)
    public List<AppConfig> currentValues() {
        List<String> keys = repository.findDistinctConfigKeys();
        if (keys.isEmpty()) {
            return List.of();
        }
        return repository.findLatestEffectiveByConfigKeys(keys, Instant.now()).stream()
                .sorted(Comparator.comparing(AppConfig::getConfigKey))
                .toList();
    }

    @Transactional(readOnly = true)
    public AppConfig current(String key) {
        return repository.findFirstByConfigKeyAndEffectiveAtLessThanEqualOrderByEffectiveAtDesc(key, Instant.now())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "CONFIG_KEY_NOT_FOUND"));
    }

    @Transactional(readOnly = true)
    public List<AppConfig> history(String key) {
        return repository.findTop10ByConfigKeyOrderByEffectiveAtDescIdDesc(key);
    }

    /**
     * basedOn 은 화면을 열 때 본 행의 publicId 다. 그 사이 다른 사람이 바꿨으면 덮어쓰지 않는다.
     * 검증에 실패하면 화면에 보여줄 문구로 IllegalArgumentException 을 던진다.
     */
    @Transactional
    public void change(String key, String basedOn, String input, String changedBy, String reason) {
        if (key.startsWith("keycap.passive.")) {
            repository.findFirstByConfigKeyOrderByIdAsc(com.ggukmoney.beanzip.global.config.KeycapPassivePolicyConfig.KEY_ENABLED)
                    .orElseThrow(() -> new IllegalArgumentException("패시브 기본 설정을 먼저 등록하세요."));
        }
        AppConfig current = current(key);
        PolicyValueRules.Rule rule = rules.find(key)
                .orElseThrow(() -> new IllegalArgumentException("이 키는 화면에서 바꿀 수 없습니다. SQL 로 바꾸세요."));
        if (!current.getPublicId().toString().equals(basedOn)) {
            throw new IllegalArgumentException("그 사이 다른 사람이 값을 바꿨습니다. 새로고침한 뒤 다시 확인하세요.");
        }
        String author = required(changedBy, MAX_CHANGED_BY, "변경자");
        String why = required(reason, MAX_REASON, "변경 사유");
        String next = validator.normalize(rule, current.getConfigValue(), input);

        Instant effectiveAt = Instant.now();
        if (key.startsWith("keycap.passive.")) {
            var passive = new java.util.HashMap<>(com.ggukmoney.beanzip.global.config.KeycapPassivePolicyConfig.DEFAULT_VALUES);
            repository.findLatestEffectiveByConfigKeys(passive.keySet(), effectiveAt)
                    .forEach(row -> passive.put(row.getConfigKey(),row.getConfigValue()));
            passive.put(key,next);
            if (key.equals(com.ggukmoney.beanzip.global.config.KeycapPassivePolicyConfig.KEY_ENABLED_AT)) {
                throw new IllegalArgumentException("활성화 시각은 스위치를 켤 때 자동으로 기록됩니다.");
            }
            if (key.equals(com.ggukmoney.beanzip.global.config.KeycapPassivePolicyConfig.KEY_ENABLED)
                    && "true".equals(next) && !"true".equals(current.getConfigValue())) {
                String activated = "\""+effectiveAt+"\"";
                passive.put(com.ggukmoney.beanzip.global.config.KeycapPassivePolicyConfig.KEY_ENABLED_AT,activated);
                com.ggukmoney.beanzip.global.config.KeycapPassivePolicyConfig.decode(passive);
                repository.save(AppConfig.change(com.ggukmoney.beanzip.global.config.KeycapPassivePolicyConfig.KEY_ENABLED_AT,
                        activated,effectiveAt,author,why));
            } else {
                com.ggukmoney.beanzip.global.config.KeycapPassivePolicyConfig.decode(passive);
            }
        }
        repository.save(AppConfig.change(key, next, effectiveAt, author, why));
        log.info("OPS_CONFIG_CHANGED key={} from={} to={} by={} reason={}", key, current.getConfigValue(), next, author, why);
    }

    private static String required(String value, int max, String label) {
        String trimmed = value == null ? "" : value.trim();
        if (!StringUtils.hasText(trimmed)) {
            throw new IllegalArgumentException(label + "를 입력하세요.");
        }
        if (trimmed.length() > max) {
            throw new IllegalArgumentException(label + "는 " + max + "자 이하로 입력하세요.");
        }
        if (CONTROL_CHARACTERS.matcher(trimmed).find()) {
            throw new IllegalArgumentException(label + "에 줄바꿈이나 제어 문자를 넣을 수 없습니다.");
        }
        return trimmed;
    }
}
