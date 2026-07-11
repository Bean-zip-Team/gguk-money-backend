package com.ggukmoney.beanzip.global.config;

import com.ggukmoney.beanzip.global.config.entity.AppConfig;
import com.ggukmoney.beanzip.global.config.repository.AppConfigRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
@RequiredArgsConstructor
public class TapConfigSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(TapConfigSeeder.class);

    private final AppConfigRepository appConfigRepository;

    @Override
    public void run(String... args) {
        try {
            Instant now = Instant.now();
            TapPolicyConfig.DEFAULT_VALUES.forEach((key, value) -> {
                if (!appConfigRepository.existsByConfigKey(key)) {
                    appConfigRepository.save(AppConfig.createFor(key, value, now));
                }
            });
        } catch (RuntimeException exception) {
            log.warn("Failed to seed default tap policy AppConfig rows; defaults will be used until this succeeds", exception);
        }
    }
}
