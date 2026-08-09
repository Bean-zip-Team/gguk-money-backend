package com.ggukmoney.beanzip.global.config;

import com.ggukmoney.beanzip.global.config.entity.AppConfig;
import com.ggukmoney.beanzip.global.config.repository.AppConfigRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class TapConfigSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(TapConfigSeeder.class);

    private final AppConfigRepository appConfigRepository;

    @Override
    public void run(String... args) {
        try {
            Instant now = Instant.now();
            syncDefaults(TapPolicyConfig.DEFAULT_VALUES, now);
            syncDefaults(CashoutPolicyConfig.DEFAULT_VALUES, now);
            syncDefaults(KeycapBoxPolicyConfig.DEFAULT_VALUES, now);
            syncDefaults(OnboardingRewardConfig.DEFAULT_VALUES, now);
        } catch (RuntimeException exception) {
            log.warn("Failed to sync default policy AppConfig rows; defaults will be used until this succeeds", exception);
        }
    }

    /**
     * Keeps AppConfig in sync with the code's DEFAULT_VALUES: if a key has never been
     * seeded, or its latest effective value differs from the code default, a new
     * versioned row is inserted so that "code deploy" == "policy rollout". Existing
     * history is never mutated (append-only), so an operator who intentionally
     * overrides a value in AppConfig will have it reverted on the next deploy that
     * still carries the old code default for that key.
     */
    private void syncDefaults(Map<String, String> defaultValues, Instant now) {
        defaultValues.forEach((key, codeValue) -> {
            Optional<AppConfig> latest =
                    appConfigRepository.findFirstByConfigKeyAndEffectiveAtLessThanEqualOrderByEffectiveAtDesc(key, now);
            if (latest.isEmpty() || !latest.get().getConfigValue().equals(codeValue)) {
                appConfigRepository.save(AppConfig.createFor(key, codeValue, now));
            }
        });
    }
}
