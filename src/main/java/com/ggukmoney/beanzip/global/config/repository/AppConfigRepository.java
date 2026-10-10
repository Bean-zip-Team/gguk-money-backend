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

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    Optional<AppConfig> findFirstByConfigKeyOrderByIdAsc(String configKey);

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

    /** One database snapshot: latest policy values plus the complete append-only activation history. */
    @Query(value = """
            SELECT c.* FROM app_config c
            WHERE c.config_key IN (:configKeys) AND c.effective_at <= :now
              AND (c.config_key = :historyKey OR c.id IN (
                SELECT DISTINCT ON (config_key) id FROM app_config
                WHERE config_key IN (:configKeys) AND effective_at <= :now
                ORDER BY config_key, effective_at DESC, id DESC))
            ORDER BY c.effective_at ASC, c.id ASC
            """, nativeQuery = true)
    List<AppConfig> findLatestEffectiveWithHistory(
            @Param("configKeys") Collection<String> configKeys,
            @Param("historyKey") String historyKey, @Param("now") Instant now);

    List<AppConfig> findTop10ByConfigKeyOrderByEffectiveAtDescIdDesc(String configKey);

    @Query("select distinct c.configKey from AppConfig c")
    List<String> findDistinctConfigKeys();
}
