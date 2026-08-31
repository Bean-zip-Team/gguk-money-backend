package com.ggukmoney.beanzip.global.config.repository;

import com.ggukmoney.beanzip.global.config.entity.AppConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AppConfigRepository extends JpaRepository<AppConfig, Long> {

    Optional<AppConfig> findByPublicId(UUID publicId);

    Optional<AppConfig> findFirstByConfigKeyAndEffectiveAtLessThanEqualOrderByEffectiveAtDesc(String configKey, Instant now);

    @Query(value = """
            SELECT DISTINCT ON (config_key) *
            FROM app_config
            WHERE config_key IN (:configKeys)
              AND effective_at <= :now
            ORDER BY config_key, effective_at DESC, id DESC
            """, nativeQuery = true)
    List<AppConfig> findLatestEffectiveByConfigKeys(
            @Param("configKeys") Collection<String> configKeys,
            @Param("now") Instant now
    );

    boolean existsByConfigKey(String configKey);

    @Query("select distinct c.configKey from AppConfig c")
    List<String> findDistinctConfigKeys();
}
