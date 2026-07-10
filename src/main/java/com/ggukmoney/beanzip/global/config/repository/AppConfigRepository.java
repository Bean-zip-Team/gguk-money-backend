package com.ggukmoney.beanzip.global.config.repository;

import com.ggukmoney.beanzip.global.config.entity.AppConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface AppConfigRepository extends JpaRepository<AppConfig, Long> {

    Optional<AppConfig> findByPublicId(UUID publicId);

    Optional<AppConfig> findFirstByConfigKeyAndEffectiveAtLessThanEqualOrderByEffectiveAtDesc(String configKey, Instant now);

    boolean existsByConfigKey(String configKey);
}
