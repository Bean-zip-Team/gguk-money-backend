package com.ggukmoney.beanzip.global.config;

import com.ggukmoney.beanzip.global.config.entity.AppConfig;
import com.ggukmoney.beanzip.global.config.repository.AppConfigRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Component
public class TapConfigSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(TapConfigSeeder.class);

    private final AppConfigRepository appConfigRepository;
    /**
     * false 면 코드 기본값과 다른 값을 되돌리지 않고, 없는 키만 채운다. 알파처럼 사람이 정책 값을
     * 바꿔 가며 시험하는 환경에서 끈다. develop 머지마다 자동 배포되므로 켜 두면 바꾼 값이 매번 사라진다.
     */
    private final boolean revertOverrides;

    public TapConfigSeeder(
            AppConfigRepository appConfigRepository,
            @Value("${app.policy.revert-overrides:true}") boolean revertOverrides
    ) {
        this.appConfigRepository = appConfigRepository;
        this.revertOverrides = revertOverrides;
    }

    /** 코드가 관리하는 정책 키 접두사. 이 접두사를 쓰면서 코드에 없는 키는 폐기된 잔재로 본다. */
    private static final List<String> MANAGED_PREFIXES = List.of("tap.", "cashout.", "keycapBox.", "onboarding.");

    @Override
    public void run(String... args) {
        try {
            Instant now = Instant.now();
            Map<String, String> managed = new LinkedHashMap<>();
            managed.putAll(TapPolicyConfig.DEFAULT_VALUES);
            managed.putAll(CashoutPolicyConfig.DEFAULT_VALUES);
            managed.putAll(KeycapBoxPolicyConfig.DEFAULT_VALUES);
            managed.putAll(OnboardingRewardConfig.DEFAULT_VALUES);

            syncDefaults(managed, now);
            warnOnOrphanKeys(managed.keySet());
        } catch (RuntimeException exception) {
            log.warn("Failed to sync default policy AppConfig rows; defaults will be used until this succeeds", exception);
        }
    }

    /**
     * 코드에서 사라진 정책 키가 AppConfig 에 남아 있으면 경고한다.
     *
     * <p>syncDefaults 는 append-only 라 키를 지우지 않는다. 그래서 정책이 바뀌어 코드에서 키를
     * 없애도 DB 행은 그대로 남고, 나중에 테이블을 열어본 사람은 그 키가 아직 살아 있다고 오해한다
     * (실제로 tap.box.session.variance 가 이렇게 남아 "상자 간격에 랜덤이 있다"는 오해를 만들었다).
     * 지우는 건 사람이 판단할 일이라 여기서는 알리기만 한다.
     */
    private void warnOnOrphanKeys(Set<String> managedKeys) {
        List<String> orphans = appConfigRepository.findDistinctConfigKeys().stream()
                .filter(key -> MANAGED_PREFIXES.stream().anyMatch(key::startsWith))
                .filter(key -> !managedKeys.contains(key))
                .sorted()
                .toList();
        if (!orphans.isEmpty()) {
            log.warn("AppConfig 에 코드가 읽지 않는 정책 키가 남아 있습니다 (값을 바꿔도 동작에 반영되지 않습니다): {}", orphans);
        }
    }

    /**
     * Keeps AppConfig in sync with the code's DEFAULT_VALUES: if a key has never been
     * seeded, or its latest effective value differs from the code default, a new
     * versioned row is inserted so that "code deploy" == "policy rollout" (only while
     * revertOverrides is on; with it off, existing keys keep their value). Existing
     * history is never mutated (append-only), so an operator who intentionally
     * overrides a value in AppConfig will have it reverted on the next deploy that
     * still carries the old code default for that key.
     */
    private void syncDefaults(Map<String, String> defaultValues, Instant now) {
        defaultValues.forEach((key, codeValue) -> {
            Optional<AppConfig> latest =
                    appConfigRepository.findFirstByConfigKeyAndEffectiveAtLessThanEqualOrderByEffectiveAtDesc(key, now);
            boolean overridden = latest.isPresent() && !latest.get().getConfigValue().equals(codeValue);
            if (latest.isEmpty() || (overridden && revertOverrides)) {
                appConfigRepository.save(AppConfig.createFor(key, codeValue, now));
            }
        });
    }
}
